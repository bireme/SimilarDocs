/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/


package org.bireme.sd

import java.io.File
import java.text.{DateFormat, SimpleDateFormat}
import java.util
import java.util.{Calendar, GregorianCalendar, TimeZone}

import org.apache.lucene.analysis.core.KeywordAnalyzer
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.analysis.{Analyzer, TokenStream}
import org.apache.lucene.index.{DirectoryReader, IndexableField, Term}
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search._
import org.apache.lucene.store.FSDirectory
import org.bireme.sd.service.Conf

import scala.collection.JavaConverters._

/** Class that looks for similar documents of given ones
  *
  * @param sdIndexPath similar documents index path
  * @param decsIndexPath decs index path
  */
class SimDocsSearch(val sdIndexPath: String,
                    val decsIndexPath: String) {
  require(sdIndexPath != null)
  require(decsIndexPath != null)

  val maxWords = 100 /*20*/   // limit the max number of words to be used as input text

  val sdDirectory: FSDirectory = FSDirectory.open(new File(sdIndexPath).toPath)
  val decsDirectory: FSDirectory = FSDirectory.open(new File(decsIndexPath).toPath)
  val decsReader: DirectoryReader = getReader
  val decsSearcher: IndexSearcher = new IndexSearcher(decsReader)

  val now: GregorianCalendar = new GregorianCalendar(TimeZone.getDefault)
  val year: Int = now.get(Calendar.YEAR)
  val month: Int = now.get(Calendar.MONTH)
  val day: Int = now.get(Calendar.DAY_OF_MONTH)
  val todayCal: GregorianCalendar = new GregorianCalendar(year, month, day, 0, 0) // begin of today
  val formatter: DateFormat = new SimpleDateFormat("yyyyMMdd")
  val today: String = formatter.format(todayCal.getTime)

  def close(): Unit = {
    sdDirectory.close()
    decsReader.close()
    decsDirectory.close()
  }

  /**
    * Searches for documents having a string in some fields
    *
    * @param text the text to be searched
    * @param outFields name of the fields that will be show in the output
    * @param sources filter the valid values of the document field 'db'
    * @param lastDays filter documents whose 'update_date' is younger or equal to lastDays days
    * @param explain if true add original, similar and common ngrams to the each outputed similar document
    * @return a json string of the similar documents
    */
  def search(text: String,
             outFields: Set[String],
             sources: Set[String],
             lastDays: Int,
             explain: Boolean): String = {
    require((text != null) && (!text.isEmpty))

    val days: Option[Int] = if (lastDays <= 0) None else Some(lastDays)
    val srcs: Option[Set[String]] = if ((sources == null) || sources.isEmpty) None else Some(sources)

    val docs: List[(Float, Map[String, List[String]])] =
      search(text, outFields, service.Conf.maxDocs, service.Conf.minNGrams, srcs, days)
    val docs2 = docs.map {
      doc =>
        val ngrams = if (explain) Some(getCommonNGrams(text, doc._2)) else None
        (doc._1, doc._2, ngrams)
    }
    doc2xml(docs2)
  }

  /**
    * Searches for documents having a string in some fields
    *
    * @param text the text to be searched
    * @param outFields name of the fields that will be show in the output
    * @param maxDocs maximum number of returned documents
    * @param minNGrams minimum number of common ngrams retrieved to consider returning a document
    * @param sources filter the valid values of the document field 'db'
    * @param lastDays filter documents whose 'update_date' is younger or equal to lastDays days
    * @return a list of pairs with document score and a map of field name and a
    *         list of its contents
    */
  def search(text: String,
             outFields: Set[String],
             maxDocs: Int,
             minNGrams: Int,
             sources: Option[Set[String]],
             lastDays: Option[Int]): List[(Float,Map[String,List[String]])] = {
    require((text != null) && text.nonEmpty)
    require(maxDocs > 0)
    require(minNGrams > 0)

    val oFields = if ((outFields == null) || outFields.isEmpty) Conf.idxFldNames + "id"
               else outFields

    searchIds(text, sources, maxDocs, minNGrams, lastDays).map {
      case (id,score) => (score, loadDoc(id, oFields))
    }
  }

  /**
    * Searches for documents having a string in some fields
    *
    * @param text the text to be searched
    * @param sources filter the valid values of the document field 'db'
    * @param maxDocs maximum number of returned documents
    * @param minNGrams minimum number of common ngrams retrieved to consider returning a document field text
    * @param lastDays filter documents whose 'update_date' is younger or equal to lastDays days
    * @return a list of pairs with document id and document score
    */
  def searchIds(text: String,
                sources: Option[Set[String]],
                maxDocs: Int,
                minNGrams: Int,
                lastDays: Option[Int]): List[(Int,Float)] = {
    require ((text != null) && text.nonEmpty)
    require (maxDocs > 0)
    require (minNGrams > 0)

    val textSeq: Seq[String] = uniformText(text)
    val text2: String = textSeq.mkString(" ")
    val analyzer: Analyzer = new NGramAnalyzer(NGSize.ngram_min_size, NGSize.ngram_max_size)
    val perFieldAnalyzer: Analyzer = {
      val hash = new util.HashMap[String,Analyzer]()
      hash.put("id", new KeywordAnalyzer())
      hash.put("db", new KeywordAnalyzer())
      hash.put("update_date", new KeywordAnalyzer())
      new PerFieldAnalyzerWrapper(analyzer, hash)
    }
    val ngrams: Seq[String] = getNGrams(text2, analyzer, maxWords)
    val dirReader: DirectoryReader = getReader
    val searcher = new IndexSearcher(dirReader)
    val lst: List[(Int,Float)] = {
      val orQuery = getQuery(text2, sources, lastDays, useDeCS = true)
//println(s"===> getIdScore docs=${searcher.search(orQuery, 10).totalHits} orQuery=$orQuery")
      getIdScore(searcher.search(orQuery, 10 * maxDocs).scoreDocs, ngrams, perFieldAnalyzer, maxDocs, minNGrams)
    }
    dirReader.close()
    lst
  }

  /**
    * Uniform the characters, delete the small words, removes stopwords and limit the number of output words
    * @param text input text
    * @return sequence of words of the input text modified
    */
  private def uniformText(text: String): Seq[String] = {
    Tools.strongUniformString(text)                            // uniform input string
      .split(" +")                                      // break the input string into tokens
      .filter(_.length >= Math.max(3, NGSize.ngram_min_size))  // delete tokens with small size
      .filter(tok => !Stopwords.All.contains(tok))             // delete tokens that are stopwords
      .take(maxWords)                                          // limit the max number of words
      .toSeq
  }

  /**
    * Create the query object to be used in a search method call.
    *
    * @param text the text to be searched
    * @param sources filter the valid values of the document field 'db'
    * @param lastDays filter documents whose 'update_date' is younger or equal to lastDays days
    * @param useDeCS if true DeCS synonyms will be added to the input text, if false the original input text will be used
    * @return the Lucene query object
    */
  private def getQuery(text: String,
                       sources: Option[Set[String]],
                       lastDays: Option[Int],
                       useDeCS: Boolean): Query = {
    require ((text != null) && text.nonEmpty)

    val queryText: Option[Query] =  Some {
      val mqParser: QueryParser =
        new QueryParser(Conf.indexedField, new NGramAnalyzer(NGSize.ngram_min_size, NGSize.ngram_max_size))
      val textImproved: String =
        if (useDeCS) OneWordDecs.addDecsSynonyms(text, decsSearcher)
        else text

      mqParser.setDefaultOperator(QueryParser.Operator.OR)
      mqParser.parse(textImproved)
    }
    val querySources: Option[Query] = sources.map {
      set =>
        val builder: BooleanQuery.Builder = new BooleanQuery.Builder()
        set.foreach(src => builder.add(new TermQuery(new Term("db", src)), BooleanClause.Occur.SHOULD))
        builder.build
    }
    val queryLastDays: Option[Query] = lastDays map {
      days =>
        val daysAgoCal: GregorianCalendar = todayCal.clone().asInstanceOf[GregorianCalendar]
        daysAgoCal.add(Calendar.DAY_OF_MONTH, -days + 1) // begin of x days ago

        val daysAgo: String = formatter.format(daysAgoCal.getTime)
        TermRangeQuery.newStringRange("update_date", daysAgo,
          today, true, true)
    }
    val qbuilder: BooleanQuery.Builder = new BooleanQuery.Builder()
    Seq(queryText, querySources, queryLastDays).flatten.foreach {
      qbuilder.add(_, BooleanClause.Occur.MUST)
    }
    qbuilder.build
  }

  /**
    * Get retrieved document's ids and scores filtering by 
    *
    * @param scoreDocs result of the Lucene search function
    * @param ngrams sequence of ngrams of the original text
    * @param analyzer Lucene analyzer class
    * @param maxDocs maximum number of returned documents
    * @param minNGrams minimum number of common ngrams retrieved to consider returning a document field text
    * @return a list of pairs with document id and document score
    */
  private def getIdScore(scoreDocs: Array[ScoreDoc],
                         ngrams: Seq[String],
                         analyzer: Analyzer,
                         maxDocs: Int,
                         minNGrams: Int): List[(Int,Float)] = {

    val aux: Array[(Int, ScoreDoc)] = scoreDocs.map {
      scoreDoc =>
        val docStr: String = loadDoc(scoreDoc.doc, service.Conf.idxFldNames)
          .foldLeft("") { case (str, (_, lst)) => str + " " + lst.mkString(" ") }
        val simNGrams: Seq[String] = getNGrams(docStr, analyzer, maxWords)
        val commonNGrams: Seq[String] = simNGrams.intersect(ngrams)
        (commonNGrams.size, scoreDoc)
    }
    val min = Math.min(minNGrams, ngrams.size)
    val aux2 = aux.filter(t => t._1 >= min).sortWith((t1, t2) => t1._1 < t2._1).reverse.take(maxDocs)

    aux2.map(t => (t._2.doc, t._2.score)).toList
  }

  /**
    * Loads the document content given its id and desired fields
    *
    * @param id Lucene document id
    * @param fields desired document fields
    * @return the document as a map of field name and a list of its contents
    */
  private def loadDoc(id: Int,
                      fields: Set[String]): Map[String,List[String]] = {
    require(id >= 0)
    require((fields != null) && fields.nonEmpty)

    val dirReader: DirectoryReader = getReader
    val map = asScalaBuffer[IndexableField](dirReader.document(id, fields.asJava).getFields()).
      foldLeft[Map[String,List[String]]] (Map()) {
      case (map2,fld) =>
        val name = fld.name()
        val lst = map2.getOrElse(name, List())
        map2 + ((name, fld.stringValue() :: lst))
    }

    dirReader.close()
    map
  }

    /**
    * Given an input text, returns all of its ngrams
    *
    * @param text input text
    * @param analyzer Lucene analyzer class
    * @param maxTokens maximum number of tokens to be returned
    * @return a sequence of ngrams
    */
  private def getNGrams(text: String,
                        analyzer: Analyzer,
                        maxTokens: Int): Seq[String] = {
    require(text != null)
    require(analyzer != null)

    val tokenStream = analyzer.tokenStream(null, text)
    val cattr: CharTermAttribute = tokenStream.addAttribute(classOf[CharTermAttribute])

    tokenStream.reset()
    val seq = getTokens(tokenStream, cattr, Seq[String](), maxTokens)

    tokenStream.end()
    tokenStream.close()

    seq
  }

  /**
    * Returns all tokens from a token stream
    *
    * @param tokenStream Lucene token stream object
    * @param cattr auxiliary object. See Lucene documentation
    * @param auxSeq temporary working seq
    * @param maxTokens maximum number of tokens to be returned
    * @return a sequence of tokens
    */
  private def getTokens(tokenStream: TokenStream,
                        cattr: CharTermAttribute,
                        auxSeq: Seq[String],
                        maxTokens: Int): Seq[String] = {
    require(tokenStream != null)
    require(cattr != null)
    require(auxSeq != null)

    if ((maxTokens > 0) && tokenStream.incrementToken()) {
      val tok = cattr.toString
      getTokens(tokenStream, cattr, auxSeq :+ tok, maxTokens - 1)
    } else auxSeq
  }

  /**
    * Given an original document and a similar one, return a triple (original doc ngrams, similar doc ngrams, common ngrams)
    * @param original the string representing the original document
    * @param doc a map representing the similar document
    * @return a triple of (original doc ngrams, similar doc ngrams, common ngrams)
    */
  def getCommonNGrams(original: String,
                      doc: Map[String,List[String]]): (List[String], List[String], List[String]) = {
    val analyzer: Analyzer = new NGramAnalyzer(NGSize.ngram_min_size, NGSize.ngram_max_size)
    val sim = getDocumentText(doc, service.Conf.idxFldNames)
    val set_original = getNGrams(Tools.strongUniformString(original), analyzer, maxWords)
    val set_similar = getNGrams(Tools.strongUniformString(sim), analyzer, maxWords)
    val set_common = set_original.intersect(set_similar)

    (set_original.toList, set_similar.toList, set_common.toList)
  }

  /**
    * Given a document represented by a map (field->content) and the desired fields, return a string representation of
    * the document
    * @param doc the input document represented as a map
    * @param fNames the desired fields used to create the output
    * @return the string representation of the document.
    */
  private def getDocumentText(doc: Map[String,List[String]],
                              fNames: Set[String]): String = {
    require(doc != null)
    require(fNames != null)

    fNames.foldLeft[String]("") {
      case (str, name) => doc.get(name) match {
        case Some(contentList) => str + " " + contentList.mkString(" ")
        case None => str
      }
    }
  }

  /**
    * Convert a document represented by a list of (score, map of fields) into
    * a json String
    *
    * @param docs a list of (score, document[map of fields])
    * @return a json string representation of the document
    **/
  def doc2json(docs: List[(Float,Map[String,List[String]])]): String = {
    require (docs != null)

    docs.zipWithIndex.foldLeft[String]("{\"documents\":[") {
      case (str, (doc,idx)) =>
        val fields = doc._2.toList.zipWithIndex
        val jflds = fields.foldLeft[String]("") {
          case (str2, (fld,idx2)) =>
            val lst: Seq[(String, Int)] = fld._2.zipWithIndex
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
        str + (if (idx == 0) "" else ",") +
        "{\"score\":" + doc._1 + "," + jflds + "}"
    } + "]}"
  }

  /**
    * Convert a document represented by a list of (score, map of fields) into
    * a xml String
    *
    * @param docs a list of (score, document[map of fields], (original_ngrams, similar_ngrams, common_ngrams))
    * @return a xml string representation of the document
    **/
  def doc2xml(docs: List[(Float,
                          Map[String,List[String]],
                          Option[(List[String], List[String], List[String])]
                          )]): String = {
    require (docs != null)

    docs.foldLeft[String]("<?xml version=\"1.0\" encoding=\"UTF-8\"?><documents>") {
      case (str, doc) =>
        val fields: List[(String, List[String])] = doc._2.toList
        val fields2 = doc._3 match {
          case Some((original, similar, common)) =>
            fields :+ ("original_ngrams" -> List(original.mkString(", "))) :+
                      ("similar_ngrams" -> List(similar.mkString(", "))) :+
                      ("common_ngrams" -> List(common.mkString(", ")))
          case None => fields
        }
        val jflds = fields2.foldLeft[String]("") {
          case (str2, fld) => fld._2.foldLeft[String](str2) {       // fld = (String,List[String])
            case (str3, content) =>
              val content2 =
                if (fld._1 equals "decs") content.replace("& ", "&amp; ")
                else content
              str3 + "<" + fld._1 + ">" + content2 + "</" + fld._1 + ">"
          }
        }
        str + "<document score=\"" + doc._1 + "\">" + jflds + "</document>"
    } + "</documents>"
  }

  /**
    * Open a new DirectoryReader if necessary, otherwise use the old one
    *
    * @return an DirectoryReader reflecting all changes made in the Lucene index
    */
  def getReader: DirectoryReader = DirectoryReader.open(sdDirectory)
}

object SimDocsSearch extends App {
  private def usage(): Unit = {
    Console.err.println("usage: SimDocsSearch" +
    "\n\t<sdIndexPath> - lucene Index where the similar document will be searched" +
    "\n\t<decsIndexPath> - lucene Index where the one word decs synonyms document will be searched" +
    "\n\t<text> - text used to look for similar documents" +
    "\n\t[<-outFields=<field>,<field>,...,<field>] - document fields used will be show in the output" +
    "\n\t[-maxDocs=<num>] - maximum number of retrieved similar documents" +
    "\n\t[-minNGrams=<num>] - minimum number of common ngrams retrieved to consider returning a document field text" +
    "\n\t[-sources=<src1>,<src2>,...,<src>] - return only docs that have the value of their field 'db' equal to <srci>" +
    "\n\t[-lastDays=<num>] - return only docs that are younger than 'lastDays' days compared to update_date flag")
    System.exit(1)
  }

  if (args.length < 3) usage()

  val parameters = args.drop(3).foldLeft[Map[String,String]](Map()) {
    case (map,par) =>
      val split = par.split(" *= *", 2)
      if (split.length == 1) map + ((split(0).substring(2), ""))
      else map + ((split(0).substring(1), split(1)))
  }

  val outFields: Set[String] = parameters.get("outFields") match {
    case Some(sFields) => sFields.split(" *, *").toSet
    case None => Set("ti", "ti_pt", "ti_en", "ti_es", "ab", "ab_pt", "ab_en", "ab_es", "decs", "id", "db", "update_date")//service.Conf.idxFldNames
  }
  val maxDocs: Int = parameters.getOrElse("maxDocs", "10").toInt
  val minNGrams: Int = parameters.getOrElse("minNGrams", Conf.minNGrams.toString).toInt
  val sources: Option[Set[String]] = parameters.get("sources").map(_.split(" *\\, *").toSet)
  val lastDays: Option[Int] = parameters.get("lastDays").map(_.toInt)
  val search: SimDocsSearch = new SimDocsSearch(args(0), args(1))
  val maxWords: Int = search.maxWords
  val docs: List[(Float,Map[String,List[String]])] = search.search(args(2), outFields, maxDocs, minNGrams, sources, lastDays)

  docs.foreach {
    case (score, doc) =>
      val (set_original, set_similar, set_common) = search.getCommonNGrams(args(2), doc)

      println("\n------------------------------------------------------")
      println(s"score: $score")
      print("original ngrams: ")
      set_original.foreach(str => print(s"[$str] "))
      print("\nsimilar ngrams: ")
      set_similar.foreach(str => print(s"[$str] "))
      print("\ncommon ngrams: ")
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
  //println(search.doc2json(docs))
}
