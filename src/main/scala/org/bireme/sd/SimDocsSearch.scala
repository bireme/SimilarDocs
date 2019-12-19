/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.sd

import java.io.File
import java.nio.file.Path
import java.text.{DateFormat, SimpleDateFormat}
import java.time.temporal.ChronoUnit
import java.util.{Calendar, Date, GregorianCalendar, TimeZone}

import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.analysis.{Analyzer, TokenStream}
import org.apache.lucene.index.{DirectoryReader, Term}
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search._
import org.apache.lucene.store.FSDirectory
import org.bireme.dh.CharSeq
import org.bireme.sd.service.Conf

//import scala.collection.JavaConverters._
import scala.jdk.CollectionConverters._

/** Class that looks for similar documents of given ones
  *
  * @param sdIndexPath similar documents index path
  * @param decsIndexPath decs index path
  */
class SimDocsSearch(val sdIndexPath: String,
                    val decsIndexPath: String) {
  require(sdIndexPath != null)
  require(decsIndexPath != null)

  val maxWords: Int = 100 /*20*/   // limit the max number of words to be used as input text

  val decsPath: Path = new File(decsIndexPath).toPath
  val decsDirectory: FSDirectory = FSDirectory.open(decsPath)
  val decsReader: DirectoryReader = DirectoryReader.open(decsDirectory)
  val decsSearcher: IndexSearcher = new IndexSearcher(decsReader)
  val decsDescriptors: Map[Char, CharSeq] = OneWordDecs.getDescriptors(decsPath.toString)

  val analyzer: Analyzer = new NGramAnalyzer(NGSize.ngram_min_size, NGSize.ngram_max_size)

  val sdDirectory: FSDirectory = FSDirectory.open(new File(sdIndexPath).toPath)
  val sdReader: DirectoryReader = DirectoryReader.open(sdDirectory)
  val sdSearcher: IndexSearcher = new IndexSearcher(sdReader)

  val now: GregorianCalendar = new GregorianCalendar(TimeZone.getDefault)
  val year: Int = now.get(Calendar.YEAR)
  val month: Int = now.get(Calendar.MONTH)
  val day: Int = now.get(Calendar.DAY_OF_MONTH)
  val todayCal: GregorianCalendar = new GregorianCalendar(year, month, day, 0, 0) // begin of today
  val formatter: DateFormat = new SimpleDateFormat("yyyyMMdd")
  val today: String = formatter.format(todayCal.getTime)
  val endDayAgo: Int = Tools.timeToDays(todayCal.getTimeInMillis - Tools.getIahxModificationTime) + Conf.excludeDays

  def close(): Unit = {
    decsReader.close()
    decsDirectory.close()
    sdReader.close()
    sdDirectory.close()
  }

  /**
    * Searches for documents having a string in some fields
    *
    * @param text the text to be searched
    * @param outFields name of the fields that will be show in the output
    * @param maxDocs maximum number of returned documents
    * @param sources filter the valid values of the document field 'db'
    * @param instances filter the valid values of the document field 'instance'
    * @param lastDays filter documents whose 'update_date' is younger or equal to lastDays days
    * @param explain if true add original, similar and common ngrams to the each outputed similar document
    * @return a json string of the similar documents
    */
  def search(text: String,
             outFields: Set[String],
             maxDocs: Int,
             sources: Set[String],
             instances: Set[String],
             lastDays: Int,
             explain: Boolean): String = {
    require((text != null) && (!text.isEmpty))

    val days: Option[Int] = if (lastDays <= 0) None else Some(lastDays)
    val srcs: Option[Set[String]] = if ((sources == null) || sources.isEmpty) None else Some(sources)
    val insts: Option[Set[String]] = if ((instances == null) || instances.isEmpty) None else Some(instances)

    val docs: List[(Int, Map[String, List[String]], Float, Set[String])] =
      search(text, outFields, maxDocs, service.Conf.minNGrams, srcs, insts, days)
    val docs2: List[(Float, Map[String, List[String]], Option[(List[String], List[String], List[String])])] =
      docs.map {
        doc =>
          val ngrams: Option[(List[String], List[String], List[String])] =
            if (explain) Some(getCommonNGrams(text, doc._2)) else None
          (doc._3, doc._2, ngrams)
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
    * @param instances filter the valid values of the document field 'instance'
    * @param lastDays filter documents whose 'update_date' is younger or equal to lastDays days
    * @return a list of pairs with document score and a map of field name and a
    *         list of its contents
    */
  def search(text: String,
             outFields: Set[String],
             maxDocs: Int,
             minNGrams: Int,
             sources: Option[Set[String]],
             instances: Option[Set[String]],
             lastDays: Option[Int]): List[(Int, Map[String, List[String]], Float, Set[String])] = {
    require((text != null) && text.nonEmpty)
    require(maxDocs > 0)
    require(minNGrams > 0)

    val text2: String = uniformText(text).mkString(" ").trim
    if (text2.isEmpty) List[(Int, Map[String, List[String]], Float, Set[String])]()
    else {
      val oFields: Set[String] = if ((outFields == null) || outFields.isEmpty) Conf.idxFldNames + "id" + "update_date"
                                 else outFields
      getDocuments(text2, sources, instances, lastDays, maxDocs, minNGrams, oFields).toList
    }
  }

  /**
    * Search a text and return an array of retrieved document info
    * @param text input text to be searched
    * @param sources set of sources to be used in the query expression
    * @param instances set of instances to be used in the query expression
    * @param daysAgo filter documents whose 'update_date' is younger or equal to lastDays days
    * @param maxDocs maximum number of returned documents
    * @param minNGrams minimum number of common ngrams retrieved to consider returning a document
    * @param outFields name of the fields that will be show in the output
    * @return a list of tuples of (Lucene document id, document fields, document score, common ngrams of the document)
    */
  private def getDocuments(text: String,
                           sources: Option[Set[String]],
                           instances: Option[Set[String]],
                           daysAgo: Option[Int],
                           maxDocs: Int,
                           minNGrams: Int,
                           outFields: Set[String]): Array[(Int, Map[String,List[String]], Float, Set[String])] = {
    val daysAgo2: Int = daysAgo.getOrElse(Integer.MAX_VALUE)

    getDocuments(text, sources, instances, daysAgo2, curDay=endDayAgo, maxDocs, minNGrams, outFields)
  }

  private def getDocuments(text: String,
                           sources: Option[Set[String]],
                           instances: Option[Set[String]],
                           daysAgo: Int,
                           curDay: Int,
                           maxDocs: Int,
                           minNGrams: Int,
                           outFields: Set[String]): Array[(Int, Map[String,List[String]], Float, Set[String])] = {
    getBeginEndDate(daysAgo, curDay).foldLeft(Array[(Int, Map[String,List[String]], Float, Set[String])]()) {
      case (array, (begin,end)) =>
        val orQuery: Query = getQuery(text, sources, instances, Some(begin), Some(end))
        val meta: Array[(Map[String,List[String]], ScoreDoc, Set[String])] =
          getDocMeta(text, orQuery, maxDocs, minNGrams, outFields)
        val docs: Array[(Int, Map[String, List[String]], Float, Set[String])] =
          array ++ meta.map(t => (t._2.doc, t._1, t._2.score, t._3))
        if (docs.length < maxDocs) {
          docs ++ getDocuments(text, sources, instances, daysAgo, getDaysAgo(begin) + 1, maxDocs - docs.length,
                               minNGrams, outFields)
        } else docs
    }
  }

  /**
    * Get a range of days considering the current day and the oldest acceptable day
    * @param daysAgo the oldest day that is allowed
    * @param curDay the current day used to consider the day range
    * @return (initial calendar day, end calendar day)
    */
  private def getBeginEndDate(daysAgo: Int,
                              curDay: Int): Option[(Calendar, Calendar)] = {
    def getDayRange: Option[(Int, Int)] = {
      curDay match {
        case x if x <= 10 => Some(10, 0)
        case x if x >= 11 && x <= 30 => Some(30, 11)
        case x if x >= 31 && x <= 180 => Some(180, 31)
        case x if x >= 181 && x <= 1095 => Some(1095, 181)
        case x if x >= 1096 && x <= 3650 => Some(3650, 1096)
        case x if x >= 3651 => Some(17800, 3671)
        case _ => None
      }
    }

    if (curDay > daysAgo) None
    else getDayRange.map {
      case (begin, end) => (getDaysAgoCalendar(begin), getDaysAgoCalendar(end))
    }
  }

  /**
    * Get retrieved document's fields, scores and set of common ngrams
    *
    * @param query Lucene query
    * @param maxDocs maximum number of returned documents
    * @param minNGrams minimum number of common ngrams retrieved to consider returning a document
    * @param outFields name of the fields that will be show in the output
    * @return an array of tuples of (Lucene document fields, score doc, set of common ngrams)
    */
  private def getDocMeta(text: String,
                         query: Query,
                         maxDocs: Int,
                         minNGrams: Int,
                         outFields: Set[String]): Array[(Map[String,List[String]], ScoreDoc, Set[String])] = {
    val ngrams: Set[String] = getNGrams(text, analyzer, maxWords)
    val minNGrams2: Int = getMinNGrams(minNGrams, ngrams)
    val result1: Array[(Map[String, List[String]], ScoreDoc, Set[String])] =
      getDocMeta(text, query, maxDocs, minNGrams2, outFields, Array[(Map[String,List[String]], ScoreDoc, Set[String])](), None)

    val orderByNgrams: Boolean = true

    val result2: Array[(Map[String, List[String]], ScoreDoc, Set[String])] =
      if (orderByNgrams) result1.sortWith(orderByNumNgrams)
      else result1

    result2.map(t => (t._1.filter(kv => outFields.contains(kv._1)), t._2, t._3))
  }

  /**
    * Get retrieved document's fields, scores and common ngrams
    *
    * @param query Lucene query
    * @param maxDocs maximum number of returned documents
    * @param minNGrams minimum number of common ngrams retrieved to consider returning a document
    * @param outFields name of the fields that will be show in the output
    * @param aux documents already processed
    * @return an array of tuples of (Lucene document fields, score doc, common ngrams)
    */
  @scala.annotation.tailrec
  private def getDocMeta(text: String,
                         query: Query,
                         maxDocs: Int,
                         minNGrams: Int,
                         outFields: Set[String],
                         aux: Array[(Map[String,List[String]], ScoreDoc, Set[String])],
                         lastScoreDoc: Option[ScoreDoc]): Array[(Map[String,List[String]], ScoreDoc, Set[String])] = {
    val scoreDocs: Array[ScoreDoc] =
      if (aux.isEmpty) sdSearcher.search(query, maxDocs).scoreDocs
      else sdSearcher.searchAfter(lastScoreDoc.get, query, maxDocs).scoreDocs

    val tuples1: Array[(Map[String, List[String]], ScoreDoc)] = scoreDocs.map {
      scoreDoc => (loadDoc(scoreDoc.doc, outFields ++ service.Conf.idxFldNames  ++ Set("update_date")), scoreDoc)
    }
    val tuples2: Array[(Map[String, List[String]], ScoreDoc, Set[String])] = tuples1.map {
      case (fields, scoreDoc) => (fields, scoreDoc, getCommonNGrams(text, fields)._3.toSet)
    }
    val tuples3: Array[(Map[String, List[String]], ScoreDoc, Set[String])] = tuples2.filter(_._3.size >= minNGrams)
    val result: Array[(Map[String, List[String]], ScoreDoc, Set[String])] = aux ++ tuples3
    if (result.length < maxDocs) getDocMeta(text, query, maxDocs, minNGrams, outFields, result, scoreDocs.lastOption)
    else result
  }

  private def orderByNumNgrams(e1: (Map[String,List[String]], ScoreDoc, Set[String]),
                               e2: (Map[String,List[String]], ScoreDoc, Set[String])): Boolean = {
    if (e1._3.size > e2._3.size) true
    else if (e1._3.size == e2._3.size)  {
      if (e1._2.score > e2._2.score) true
      else if (e1._2.score == e2._2.score) {
        val upd_time1: String = e1._1.getOrElse("update_date", List("")).head
        val upd_time2: String = e2._1.getOrElse("update_date", List("")).head
        upd_time1.compareTo(upd_time2) > 0
      } else false
    } else false
  }

  /*
  private def orderByDate(elem: ((Map[String,List[String]], ScoreDoc, Set[String]),
                                 (Map[String,List[String]], ScoreDoc, Set[String]))): Boolean = {
    val t1: String = elem._1._1.getOrElse("update_time", List("")).head
    val t2: String = elem._2._1.getOrElse("update_time", List("")).head
    val comp: Int = t1.compareTo(t2)

    if (comp > 0) true
    else if (comp == 0) elem._1._2.score > elem._2._2.score
    else true
  }
  */

  /**
    * Retrieve the number of minimum ngrams according to the input text
    * @param minNGrams suggested number of minimum ngrams
    * @param ngrams number of ngrams found in the input text
    * @return the "optimal" number of minimum ngrams
    */
  private def getMinNGrams(minNGrams: Int,
                           ngrams: Set[String]): Int = {
    val nsize: Int = ngrams.size

    if (nsize <= 2) Math.max(1, Math.min(nsize, minNGrams))
    else if (nsize <= 5) Math.max(2, Math.min(nsize, minNGrams))
    else if (nsize <= 19) Math.max(3, Math.min(nsize, minNGrams))
    else Math.max(4, Math.min(nsize, minNGrams))
  }

  /**
    * @param days number the days ago to get the calendar
    * @return the calendar of X days ago
    */
  private def getDaysAgoCalendar(days: Int): Calendar = {
    assert (days >= 0)

    if (days == 0) todayCal
    else {
      val dAgoCal: GregorianCalendar = todayCal.clone().asInstanceOf[GregorianCalendar]
      dAgoCal.add(Calendar.DAY_OF_MONTH, -days + 1)
      dAgoCal
    }
  }

  /**
    *
    * @param cal the input Calendar
    * @return the number of days from today until the calendar day
    */
  private def getDaysAgo(cal: Calendar): Int = {
    val daysBetween: Long = ChronoUnit.DAYS.between(cal.toInstant, todayCal.toInstant) + 1
    daysBetween.toInt
  }

  /**
    * Uniform the characters, delete the small words, removes stopwords and limit the number of output words
    * @param text input text
    * @return set of words of the input text modified
    */
  private def uniformText(text: String): Set[String] = {
    Tools.strongUniformString(text)                            // uniform input string
      .split(" +")                                             // break the input string into tokens
      .filter(_.length >= Math.max(3, NGSize.ngram_min_size))  // delete tokens with small size
      .filter(tok => !Stopwords.All.contains(tok))             // delete tokens that are stopwords
      .toSet                                                   // transform an array into a set
      .take(maxWords)                                          // limit the max number of words
  }

  /**
    * Create the query object to be used in a search method call.
    *
    * @param text the text to be searched
    * @param sources filter the valid values of the document field 'db'
    * @param instances filter the valid values of the document field 'instance'
    * @param fromDate filter documents that are younger or equal to some date
    * @param toDate filter documents that are older or equal to some date
    * @return the Lucene query object
    */
  private def getQuery(text: String,
                       sources: Option[Set[String]],
                       instances: Option[Set[String]],
                       fromDate: Option[Calendar],
                       toDate: Option[Calendar]): Query = {
    require ((text != null) && text.nonEmpty)

    val queryText: Option[Query] = Some {
      val mqParser: QueryParser =
        new QueryParser(Conf.indexedField, new NGramAnalyzer(NGSize.ngram_min_size, NGSize.ngram_max_size))
      val textImproved: String = OneWordDecs.addDecsSynonyms(text, decsSearcher, decsDescriptors)

      mqParser.setDefaultOperator(QueryParser.Operator.OR)
      mqParser.parse(textImproved)
    }
    val querySources: Option[Query] = sources.map {
      set =>
        val builder: BooleanQuery.Builder = new BooleanQuery.Builder()
        set.foreach(src => builder.add(new TermQuery(new Term("db", src)), BooleanClause.Occur.SHOULD))
        builder.build
    }
    val queryInstances: Option[Query] = instances.map {
      set =>
        val builder: BooleanQuery.Builder = new BooleanQuery.Builder()
        set.foreach(insts => builder.add(new TermQuery(new Term("instance", insts)), BooleanClause.Occur.SHOULD))
        builder.build
    }
    val queryLastDays: Option[Query] = if (fromDate.isEmpty && toDate.isEmpty) None
    else {
      val fromDateStr: String = formatter.format(fromDate.map(_.getTime).getOrElse(new Date(0)))
      val toDateStr: String = formatter.format(toDate.getOrElse(todayCal).getTime)
      Some(TermRangeQuery.newStringRange("update_date", fromDateStr, toDateStr, true, true))
    }

    val qbuilder: BooleanQuery.Builder = new BooleanQuery.Builder()
    Seq(queryText, querySources, queryInstances, queryLastDays).flatten.foreach {
      qbuilder.add(_, BooleanClause.Occur.MUST)
    }
    qbuilder.build
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

    //asScalaBuffer[IndexableField](sdReader.document(id, fields.asJava).getFields()).
    sdReader.document(id, fields.asJava).getFields().asScala.
      foldLeft[Map[String,List[String]]] (Map()) {
      case (map2, fld) =>
        val name: String = fld.name()
        val lst: List[String] = map2.getOrElse(name, List())
        map2 + ((name, fld.stringValue() :: lst))
    }
  }

    /**
    * Given an input text, returns all of its ngrams
    *
    * @param text input text
    * @param analyzer Lucene analyzer class
    * @param maxTokens maximum number of tokens to be returned
    * @return a set of ngrams
    */
  private def getNGrams(text: String,
                        analyzer: Analyzer,
                        maxTokens: Int): Set[String] = {
    require(text != null)
    require(analyzer != null)

    val tokenStream: TokenStream = analyzer.tokenStream(null, text.trim)
    val cattr: CharTermAttribute = tokenStream.addAttribute(classOf[CharTermAttribute])

    tokenStream.reset()
    val set: Set[String] = getTokens(tokenStream, cattr, Set[String](), maxTokens)

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
    * @param maxTokens maximum number of tokens to be returned
    * @return a set of tokens
    */
  @scala.annotation.tailrec
  private def getTokens(tokenStream: TokenStream,
                        cattr: CharTermAttribute,
                        auxSet: Set[String],
                        maxTokens: Int): Set[String] = {
    require(tokenStream != null)
    require(cattr != null)
    require(auxSet != null)

    if ((maxTokens > 0) && tokenStream.incrementToken()) {
      val tok = cattr.toString
      if (auxSet.contains(tok)) getTokens(tokenStream, cattr, auxSet, maxTokens)
      else getTokens(tokenStream, cattr, auxSet + tok, maxTokens - 1)
    } else auxSet
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
    val docSet: Set[String] = doc.foldLeft(Set[String]()) {
      case (set, kv) =>
        if (kv._1.equals("update_date")) set
        else set ++ kv._2
    }
    val sim: String = docSet.mkString(" ")
    val set_original: Set[String] = getNGrams(Tools.uniformString(original), analyzer, maxWords)
    //val set_original: Set[String] = getNGrams(Tools.strongUniformString(original), analyzer, maxWords)
    val set_similar: Set[String] = getNGrams(Tools.uniformString(sim), analyzer, maxWords)
    //val set_similar: Set[String] = getNGrams(Tools.strongUniformString(sim), analyzer, maxWords)
    val set_common: Set[String] = set_original.intersect(set_similar)

    (set_original.toList, set_similar.toList, set_common.toList)
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
        val fields: List[((String, List[String]), Int)] = doc._2.toList.zipWithIndex
        val jflds: String = fields.foldLeft[String]("") {
          case (str2, (fld,idx2)) =>
            val lst: Seq[(String, Int)] = fld._2.zipWithIndex
            val lstStr: String = lst.size match {
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
        val fields2: List[(String, List[String])] = doc._3 match {
          case Some((original, similar, common)) =>
            fields :+ ("original_ngrams" -> List(original.mkString(", "))) :+
                      ("similar_ngrams" -> List(similar.mkString(", "))) :+
                      ("common_ngrams" -> List(common.mkString(", ")))
          case None => fields
        }
        val jflds: String = fields2.foldLeft[String]("") {
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
}

object SimDocsSearch extends App {
  private def usage(): Unit = {
    Console.err.println("usage: SimDocsSearch" +
    "\n\t-sdIndex=<sdIndexPath> - lucene Index where the similar document will be searched" +
    "\n\t-decsIndex=<decsIndexPath> - lucene Index where the one word decs synonyms document will be searched" +
    "\n\t-text=<str> - text used to look for similar documents" +
    "\n\t[<-outFields=<field>,<field>,...,<field>] - document fields used will be show in the output" +
    "\n\t[-maxDocs=<num>] - maximum number of retrieved similar documents" +
    "\n\t[-minNGrams=<num>] - minimum number of common ngrams retrieved to consider returning a document field text" +
    "\n\t[-sources=<src1>,<src2>,...,<src>] - return only docs that have the value of their field 'db' equal to <srci>" +
    "\n\t[-instances=<inst1>,<inst2>,...,<inst>] - return only docs that have the value of their field 'instance' equal to <insti>" +
    "\n\t[-lastDays=<num>] - return only docs that are younger than 'lastDays' days compared to update_date flag")
    System.exit(1)
  }

  if (args.length < 3) usage()

  println("Starting search ...")

  val startTime: Long = new Date().getTime
  val parameters = args.foldLeft[Map[String,String]](Map()) {
    case (map,par) =>
      val split = par.split(" *= *", 2)
      if (split.length == 1) map + ((split(0).substring(2), ""))
      else map + ((split(0).substring(1), split(1)))
  }

  val sdIndex: String = parameters("sdIndex")
  val decsIndex: String = parameters("decsIndex")
  val text: String = parameters("text")
  val outFields: Set[String] = parameters.get("outFields") match {
    case Some(sFields) => sFields.split(" *, *").toSet
    case None => Set("ti", "ti_pt", "ti_en", "ti_es", "ab", "ab_pt", "ab_en", "ab_es", "decs", "id", "db", "update_date")//service.Conf.idxFldNames
  }
  val maxDocs: Int = parameters.getOrElse("maxDocs", "10").toInt
  val minNGrams: Int = parameters.getOrElse("minNGrams", Conf.minNGrams.toString).toInt
  val sources: Option[Set[String]] = parameters.get("sources").map(_.split(" *, *").toSet)
  val instances: Option[Set[String]] = parameters.get("instances").map(_.split(" *, *").toSet)
  val lastDays: Option[Int] = parameters.get("lastDays").map(_.toInt)
  val search: SimDocsSearch = new SimDocsSearch(sdIndex, decsIndex)
  val maxWords: Int = search.maxWords

  val docs: List[(Int, Map[String, List[String]], Float, Set[String])] =
    search.search(text, outFields, maxDocs, minNGrams, sources,instances, lastDays)

  docs.foreach {
    case (_, doc, score, _) =>
      val (set_original, set_similar, set_common) = search.getCommonNGrams(text, doc)

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
  println(s"Elapsed time2: ${new Date().getTime - startTime}")
}
