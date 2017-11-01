/*=========================================================================

    Copyright © 2017 BIREME/PAHO/WHO

    This file is part of SimilarDocs.

    SimilarDocs is free software: you can redistribute it and/or
    modify it under the terms of the GNU Lesser General Public License as
    published by the Free Software Foundation, either version 2.1 of
    the License, or (at your option) any later version.

    SimilarDocs is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public
    License along with SimilarDocs. If not, see <http://www.gnu.org/licenses/>.

=========================================================================*/

package org.bireme.sd

import java.io.File

import java.util.{Calendar,GregorianCalendar,TimeZone}

import scala.collection.SortedMap
import scala.collection.JavaConverters._
import scala.collection.immutable.TreeSet

import org.apache.lucene.analysis.{Analyzer,TokenStream}
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.document.DateTools
import org.apache.lucene.index.{DirectoryReader,IndexableField}
import org.apache.lucene.queryparser.classic.{MultiFieldQueryParser}
import org.apache.lucene.search.{BooleanClause,BooleanQuery,IndexSearcher,ScoreDoc,TermRangeQuery}
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.BytesRef

/** Class that looks for similar documents to a given ones
  *
  * @param sdIndexPath similar documents index path
  * @param decsIndexPath decs index path
  */
class SimDocsSearch(val sdIndexPath: String,
                    val decsIndexPath: String) {
  require(sdIndexPath != null)
  require(decsIndexPath != null)

  val sdDirectory = FSDirectory.open(new File(sdIndexPath).toPath())
  val decsDirectory = FSDirectory.open(new File(decsIndexPath).toPath())
  val decsReader = DirectoryReader.open(decsDirectory)
  val decsSearcher = new IndexSearcher(decsReader)

  def close(): Unit = {
    sdDirectory.close()
    decsReader.close()
    decsDirectory.close()
  }

  /**
    * Searches for documents having a string in some fields
    *
    * @param text the text to be searched
    * @param lastDays filter documents whose 'entranceDate' is yonger or equal to x days
    * @return a json string of the similar documents
    */
  def search(text: String,
             lastDays: Int): String = {
    require((text != null) && (!text.isEmpty))

    val days = if (lastDays <= 0) None else Some(lastDays)

    doc2xml(search(text,
                   service.Conf.idxFldNames,
                   service.Conf.maxDocs,
                   service.Conf.minSim,
                   days))
  }

  /**
    * Searches for documents having a string in some fields
    *
    * @param text the text to be searched
    * @param fields document fields into where the text will be searched
    * @param maxDocs maximum number of returned documents
    * @param minSim minimum similarity between the input text and the retrieved
    *               field text
    * @param lastDays filter documents whose 'entranceDate' is yonger or equal to x days
    * @return a list of pairs with document score and a map of field name and a
    *         list of its contents
    */
  def search(text: String,
             fields: Set[String],
             maxDocs: Int,
             minSim: Float,
             lastDays: Option[Int]): List[(Float,Map[String,List[String]])] = {
    require((text != null) && (!text.isEmpty))
    require((fields != null) && (!fields.isEmpty))
    require(maxDocs > 0)
    require(minSim > 0)

    val newContent = Tools.strongUniformString(text)

    searchIds(newContent, fields, maxDocs, minSim, lastDays).map {
      case (id,score) => (score,loadDoc(id, fields))
    }
  }

  /**
    * Searches for documents having a string in some fields
    *
    * @param text the text to be searched
    * @param fields document fields into where the text will be searched
    * @param maxDocs maximum number of returned documents
    * @param minSim minimum similarity between the input text and the retrieved
    *               field text
    * @param lastDays filter documents whose 'entranceDate' is yonger or equal to x days
    * @return a list of pairs with document id and document score
    */
  def searchIds(text: String,
                fields: Set[String],
                maxDocs: Int,
                minSim: Float,
                lastDays: Option[Int]): List[(Int,Float)] = {
    require ((text != null) && (!text.isEmpty))
    require ((fields != null) && (!fields.isEmpty))
    require (maxDocs > 0)
    require (minSim > 0)

//println("entrando no searchIds / SimDocsSearch")
    val mqParser = new MultiFieldQueryParser(fields.toArray,
      new NGramAnalyzer(NGSize.ngram_min_size, NGSize.ngram_max_size))
//println(s"text=$text fields=$fields")
    val textImproved = OneWordDecs.addDecsSynonyms(text, decsSearcher)
    val query1 =  mqParser.parse(textImproved)
    val query = lastDays match {
      case Some(days) =>
        require (days > 0)
        val now = new GregorianCalendar(TimeZone.getDefault())
        val year = now.get(Calendar.YEAR)
        val month = now.get(Calendar.MONTH)
        val day = now.get(Calendar.DAY_OF_MONTH)
        val todayCal = new GregorianCalendar(year, month, day, 0, 0) // begin of today
        val today = DateTools.dateToString(todayCal.getTime(),
                                           DateTools.Resolution.DAY)
        val daysAgoCal = todayCal.clone().asInstanceOf[GregorianCalendar]
        daysAgoCal.add(Calendar.DAY_OF_MONTH, -days)                // begin of x days ago
        val daysAgo = DateTools.dateToString(daysAgoCal.getTime(),
                                             DateTools.Resolution.DAY)
        val query2 = new TermRangeQuery("entranceDate", new BytesRef(daysAgo),
                                        new BytesRef(today), true, true);
        val builder = new BooleanQuery.Builder()
        builder.add(query1, BooleanClause.Occur.MUST)
        builder.add(query2, BooleanClause.Occur.MUST)
        builder.build
      case None => query1
    }
//println("### antes do new IndexSearcher")
    val dirReader = getReader()
    val searcher = new IndexSearcher(dirReader)
//println(s"### antes do 'searcher.search' maxDocs=$maxDocs minSim=$minSim query=$query")
//println(s"### totalHits=${searcher.search(query, maxDocs).totalHits}")
    val lst = sortByDate(searcher,
                         searcher.search(query, 3  * maxDocs).scoreDocs,
                         maxDocs, minSim)
//println(s"### depois do 'searcher.search' Ids=$lst")
    dirReader.close()
    lst
  }

  /**
    * Filter a result of a search by date if their scores are bigger than a
    * limit otherwise use the default sorting that uses only the score
    *
    * @param searcher Lucene index searcher object
    * @param scoreDocs result of the Lucene search function
    * @param maxDocs maximum number of returned documents
    * @param minSim minimum similarity between the input text and the retrieved
    *               field text
    * @param minDateSim minimum similarity used to sort documents only by date
    * @return a list of pairs with document id and document score
    */
  private def sortByDate(searcher: IndexSearcher,
                         scoreDocs: Array[ScoreDoc],
                         maxDocs: Int,
                         minSim: Float,
                         minDateSim: Float = 80.0f): List[(Int,Float)] = {
    require (searcher != null)
    require (scoreDocs != null)
    require (maxDocs > 0)
    require (minSim > 0)
    require (minDateSim >= minSim)

    def sortByDate(seq: Seq[ScoreDoc],
                   curMap: SortedMap[String,(Int,Float)]):
                                               SortedMap[String,(Int,Float)] = {
      if (seq.isEmpty) curMap.take(maxDocs)
      else {
        val sdoc = seq.head
        val time = searcher.doc(sdoc.doc, Set("entry_date").asJava).get("entry_date")
        if (time == null) sortByDate(seq.tail, curMap)
        else {
          val ttime = time.trim.replaceAll("[^0-9]+", "")
          sortByDate(seq.tail, curMap + ((s"${ttime}_${sdoc.doc}",
                        (sdoc.doc, sdoc.score))))
        }
      }
    }

    val (timeSeq, othersSeq) = scoreDocs.partition(_.score >= minDateSim)

    // Key ordering
    implicit val Ord = implicitly[Ordering[String]]
    val timeSortedMap = sortByDate(timeSeq, SortedMap.
                                         empty[String,(Int,Float)](Ord.reverse))

    val dateSeq = othersSeq.filter(_.score >= minSim)
    val dateList = dateSeq.take(maxDocs - timeSortedMap.size).
                                           foldLeft[List[(Int,Float)]](List()) {
      case (lst, sdoc)=> lst :+ (sdoc.doc, sdoc.score)
    }

    timeSortedMap.foldLeft[List[(Int,Float)]](List()) {
      case (lst, (_,v)) => lst :+ v
    } ++ dateList
  }

  /**
    * Convert a document represented by a list of (score, map of fields) into
    * a json String
    *
    * @param a list of (score, document[map of fields])
    * @return a json string representation of the document
    **/
  def doc2json(docs: List[(Float,Map[String,List[String]])]): String = {
    require (docs != null)

    docs.zipWithIndex.foldLeft[String]("{\"documents\":[") {
      case (str, (doc,idx)) => {
        val fields = doc._2.toList.zipWithIndex
        val jflds = fields.foldLeft[String]("") {
          case (str2, (fld,idx2)) => {
            val lst = fld._2.zipWithIndex
            val lstStr = lst.size match {
              case 0 => ""
              case 1 => "\"" + lst.head + "\""
              case _ => "[" + lst.foldLeft[String]("") {
                case (str3,(elem,idx3)) =>
                  str3 + (if (idx3 == 0) "" else ",") + "\"" + elem + "\""
              } + "]"
            }
            str2 + (if (idx2 == 0) "" else ",") +
            "\"" + fld._1 + "\":" + lstStr
          }
        }
        str + (if (idx == 0) "" else ",") +
        "{\"score\":" + doc._1 + "," + jflds + "}"
      }
    } + "]}"
  }

  /**
    * Convert a document represented by a list of (score, map of fields) into
    * a xml String
    *
    * @param a list of (score, document[map of fields])
    * @return a xml string representation of the document
    **/
  def doc2xml(docs: List[(Float,Map[String,List[String]])]): String = {
    require (docs != null)

    docs.foldLeft[String]("<?xml version=\"1.0\" encoding=\"UTF-8\"?><documents>") {
      case (str, doc) => {
        val fields = doc._2.toList   // List[(String,List[String])]
        val jflds = fields.foldLeft[String]("") {
          case (str2, fld) => fld._2.foldLeft[String](str2) {       // fld = (String,List[String])
            case (str3, content) => str3 + "<" + fld._1 + ">" + content + "</" + fld._1 + ">"
          }
        }
        str + "<document score=\"" + doc._1 + "\">" + jflds + "</document>"
      }
    } + "</documents>"
  }

 /**
   * Open a new DirectoryReader if necessary, otherwise use the old one
   *
   * @return an DirectoryReader reflecting all changes made in the Lucene index
   */
  def getReader(): DirectoryReader = DirectoryReader.open(sdDirectory)

  /**
    * Loads the document content given its id and desired fields
    *
    * @param id Lucene document id
    * @param fields desired document fields
    * @return the document as a map of field name and a list of its contents
    */
  private def loadDoc(id: Int,
                      fields: Set[String]): Map[String,List[String]] = {
    require(id > 0)
    require((fields != null) && (!fields.isEmpty))

    val dirReader = getReader()
    val map = asScalaBuffer[IndexableField](dirReader.document(id).getFields()).
                                    foldLeft[Map[String,List[String]]] (Map()) {
      case (map,fld) =>
        val name = fld.name()
        val lst = map.getOrElse(name, List())
        map + ((name, fld.stringValue() :: lst))
    }

    dirReader.close()
    map
  }
}

object SimDocsSearch extends App {
  private def usage(): Unit = {
    Console.err.println("usage: SimDocsSearch" +
    "\n\t<sdIndexPath> - lucene Index where the similar document will be searched" +
    "\n\t<decsIndexPath> - lucene Index where the one word decs synonyms document will be searched" +
    "\n\t<text> - text used to look for similar documents" +
    "\n\t[-fields=<field>,<field>,...,<field>] - document fields used to look for similarities" +
    "\n\t[-maxDocs=<num>] - maximum number of retrieved similar documents" +
    "\n\t[-minSim=<num>] - minimum similarity level (0 to 1.0) accepted " +
    "\n\t[-lastDays=<num>] - returns only docs that are younger (entranceDate flag) than 'lastDays' days")
    System.exit(1)
  }

  if (args.length < 3) usage()

  val parameters = args.drop(3).foldLeft[Map[String,String]](Map()) {
    case (map,par) => {
      val split = par.split(" *= *", 2)
      if (split.length == 1) map + ((split(0).substring(2), ""))
      else map + ((split(0).substring(1), split(1)))
    }
  }

  val fldNames = parameters.get("fields") match {
    case Some(sFields) => sFields.split(" *, *").toSet
    case None => service.Conf.idxFldNames
  }
  val maxDocs = parameters.getOrElse("maxDocs", "10").toInt
  val minSim = parameters.getOrElse("minSim", "0.5").toFloat
  val lastDays = parameters.get("lastDays").map(_.toInt)
  val search = new SimDocsSearch(args(0), args(1))
  val docs = search.search(args(2), fldNames, maxDocs, minSim, lastDays)

  val analyzer = new NGramAnalyzer(NGSize.ngram_min_size,
                                   NGSize.ngram_max_size)
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
  //println(search.doc2json(docs))

  private def getSimilarText(doc: Map[String,List[String]],
                             fNames: Set[String]): String = {
    require(doc != null)
    require(fNames != null)

    fNames.foldLeft[String]("") {
      case (str, name) => doc.get(name) match {
        case Some(content) => str + " " + content
        case None => str
      }
    }
  }

  /**
    * Given an input text, returns all of its ngrams
    *
    * @param text input text
    * @param analyzer Lucene analyzer class
    * @return a set of ngrams
    */
  private def getNGrams(text: String,
                        analyzer: Analyzer): Set[String] = {
    require(text != null)
    require(analyzer != null)

    val tokenStream = analyzer.tokenStream(null, text)
    val cattr = tokenStream.addAttribute(classOf[CharTermAttribute])

    tokenStream.reset()
    val set = getTokens(tokenStream, cattr, TreeSet[String]())

    tokenStream.end()
    tokenStream.close()

    set
  }

  /**
    * Returns all tokens from a token stream
    *
    * @param tokenStream Lucene token stream object
    * @param cattr auxiliary object. See Lucene documentation
    * @param auxSet temporary working set
    * @return a set of tokens
    */
  private def getTokens(tokenStream: TokenStream,
                        cattr: CharTermAttribute,
                        auxSet: Set[String]): Set[String] = {
    require(tokenStream != null)
    require(cattr != null)
    require(auxSet != null)

    if (tokenStream.incrementToken()) {
      val tok = cattr.toString()
      getTokens(tokenStream, cattr, auxSet + tok)
    } else auxSet
  }
}
