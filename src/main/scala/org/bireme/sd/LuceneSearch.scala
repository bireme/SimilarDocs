package org.bireme.sd

import java.io.File

import scala.collection.JavaConverters._
import scala.collection.immutable.TreeSet

import org.apache.lucene.analysis.{Analyzer,TokenStream}
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.index.{DirectoryReader,IndexableField}
import org.apache.lucene.queryparser.classic.{MultiFieldQueryParser,QueryParser}
import org.apache.lucene.search.{IndexSearcher, TermQuery}
import org.apache.lucene.store.FSDirectory

class LuceneSearch(indexPath: String) {
  val directory = FSDirectory.open(new File(indexPath).toPath())
  val reader = DirectoryReader.open(directory)
  val searcher = new IndexSearcher(reader)

  def close(): Unit = {
    reader.close()
    directory.close()
  }

  def search(text: String,
             fields: Set[String],
             maxDocs: Int,
             minSim: Float): List[(Float,Map[String,List[String]])] = {
    searchIds(text, fields, maxDocs, minSim).map {
      case (id,score) => (score,loadDoc(id, fields))
    }
  }

  private def searchIds(text: String,
                        fields: Set[String],
                        maxDocs: Int,
                        minSim: Float): List[(Int,Float)] = {
    val mqParser = new MultiFieldQueryParser(fields.toArray,
                                           new NGramAnalyzer(NGSize.ngram_size))
    val query =  mqParser.parse(text)

    searcher.search(query, maxDocs).scoreDocs.filter(_.score >= minSim).
                                             map(sd => (sd.doc,sd.score)).toList
  }

  private def loadDoc(id: Int,
                      fields: Set[String]): Map[String,List[String]] = {
    asScalaBuffer[IndexableField](reader.document(id).getFields()).
                                    foldLeft[Map[String,List[String]]] (Map()) {
      case (map,fld) =>
        val name = fld.name()
        val lst = map.getOrElse(name, List())
        map + ((name, fld.stringValue() :: lst))
    }
  }
}

object LuceneSearch extends App {
  private def usage(): Unit = {
    Console.err.println("usage: LuceneSearch <indexPath> <text>" +
    "\n\t-fields=<field>,<field>,...,<field>" +
    "\n\t[-maxDocs=<num>]" +
    "\n\t[-minSim=<num>]")
    System.exit(1)
  }

  if (args.length < 1) usage()

  val parameters = args.drop(2).foldLeft[Map[String,String]](Map()) {
    case (map,par) => {
      val split = par.split(" *= *", 2)
      map + ((split(0).substring(1), split(1)))
    }
  }
  val sFields = parameters("fields")
  val fldNames = if (sFields.isEmpty) Set[String]()
                 else sFields.split(" *, *").toSet
  val maxDocs = parameters.getOrElse("maxDocs", "10").toInt
  val minSim = parameters.getOrElse("minSim", "0.5").toFloat
  val search = new LuceneSearch(args(0))
  val docs = search.search(args(1), fldNames, maxDocs, minSim)

  val directory = FSDirectory.open(new File(args(0)).toPath())
  val reader = DirectoryReader.open(directory)
  val searcher = new IndexSearcher(reader)
  val analyzer = new NGramAnalyzer(NGSize.ngram_size)
  val set_text = getNGrams(args(1), analyzer)

  docs.foreach {
    case (score,doc) =>
      println("\n------------------------------------------------------")
      println(s"score: $score")
      val sim = getSimilarText(doc, fldNames)
      //println(s"text=$sim")
      val set_similar = getNGrams(sim, analyzer)
      val set_common = set_text.intersect(set_similar)
      print("common ngrams: ")
      set_common.foreach(str => print(s"[$str] "))
      println("\n")
      doc.foreach {
        case(tag,list) =>
          list.foreach {
            content => println(s"[$tag]: $content")
          }
      }
  }
  search.close()
  reader.close()
  directory.close()

  private def getSimilarText(doc: Map[String,List[String]],
                             fNames: Set[String]): String = {
    fNames.foldLeft[String]("") {
      case (str, name) => doc.get(name) match {
        case Some(content) => str + " " + content
        case None => str
      }
    }
  }

  private def getNGrams(text: String,
                        analyzer: Analyzer): Set[String] = {
    val tokenStream = analyzer.tokenStream(null, text)
    val cattr = tokenStream.addAttribute(classOf[CharTermAttribute])

    tokenStream.reset()
    val set = getTokens(tokenStream, cattr, TreeSet[String]())

    tokenStream.end()
    tokenStream.close()

    set
  }

  private def getTokens(tokenStream: TokenStream,
                        cattr: CharTermAttribute,
                        auxSet: Set[String]): Set[String] = {
    if (tokenStream.incrementToken()) {
      val tok = cattr.toString()
      getTokens(tokenStream, cattr, auxSet + tok)
    } else auxSet
  }
}
