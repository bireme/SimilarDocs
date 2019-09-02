/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/


package org.bireme.sd

import java.io.File
import java.text.{DateFormat, SimpleDateFormat}
import java.util.{Calendar, Date, GregorianCalendar, TimeZone}

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

  val maxWords: Int = 100 /*20*/   // limit the max number of words to be used as input text

  val decsDirectory: FSDirectory = FSDirectory.open(new File(decsIndexPath).toPath)
  val decsReader: DirectoryReader = DirectoryReader.open(decsDirectory)
  val decsSearcher: IndexSearcher = new IndexSearcher(decsReader)

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

    val docs: List[(Float, Map[String, List[String]])] =
      search(text, outFields, maxDocs, service.Conf.minNGrams, srcs, insts, days)
    val docs2: List[(Float, Map[String, List[String]], Option[(List[String], List[String], List[String])])] =
      docs.map {
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
             lastDays: Option[Int]): List[(Float,Map[String,List[String]])] = {
    require((text != null) && text.nonEmpty)
    require(maxDocs > 0)
    require(minNGrams > 0)

    val oFields: Set[String] = if ((outFields == null) || outFields.isEmpty) Conf.idxFldNames + "id"
               else outFields

    searchIds(text, sources, instances, maxDocs, minNGrams, lastDays).map {
      case (id,score) => (score, loadDoc(id, oFields))
    }
  }

  /**
    * Searches for documents having a string in some fields
    *
    * @param text the text to be searched
    * @param sources filter the valid values of the document field 'db'
    * @param instances filter the valid values of the document field 'instance'
    * @param maxDocs maximum number of returned documents
    * @param minNGrams minimum number of common ngrams retrieved to consider returning a document
    * @param lastDays filter documents whose 'update_date' is younger or equal to lastDays days
    * @param excludeIds a set of document identifiers to exclude from output list
    * @return a list of pairs with Lucene document id and document score
    */
  def searchIds(text: String,
                sources: Option[Set[String]],
                instances: Option[Set[String]],
                maxDocs: Int,
                minNGrams: Int,
                lastDays: Option[Int],
                excludeIds: Option[Set[Int]] = None): List[(Int,Float)] = {
    require ((text != null) && text.nonEmpty)
    require (maxDocs > 0)
    require (minNGrams > 0)

    val textSet: Set[String] = uniformText(text)
    val text2: String = textSet.mkString(" ")

    if (text2.isEmpty) List[(Int,Float)]()
    else {
      val analyzer: Analyzer = new NGramAnalyzer(NGSize.ngram_min_size, NGSize.ngram_max_size)
      val ngrams: Set[String] = getNGrams(text2, analyzer, maxWords)
      val nsize: Int = ngrams.size
      val minNGrams2: Int =   // Choose the number of ngrams according to the number of ngrams of the input text
        if (nsize <= 2) Math.max(1, Math.min(nsize, minNGrams))
        else if (nsize <= 5) Math.max(2, Math.min(nsize, minNGrams))
        else if (nsize <= 19) Math.max(3, Math.min(nsize, minNGrams))
        else Math.max(4, Math.min(nsize, minNGrams))
      val multi: Int = 200

      // Try the first 1000 days (improve recent document retrieval)
      val (scoreDocs: Array[ScoreDoc], scoreSet: Set[Int]) =
        if (lastDays.isEmpty || lastDays.get > 500) {
          val orQuery: Query = getQuery(text, sources, instances, Some(500), useDeCS = true)
          val sd: Array[ScoreDoc] = sdSearcher.search(orQuery, maxDocs * multi).scoreDocs
          val ss: Set[Int] = sd.map(_.doc).toSet
          (sd, ss)
        } else (Array.empty[ScoreDoc], Set[Int]())

      // Complete with remaining documents
      val orQuery: Query = getQuery(text, sources, instances, lastDays, useDeCS = true)
      val scoreDocs1: Array[ScoreDoc] = sdSearcher.search(orQuery, maxDocs * multi).scoreDocs
      val scoreDocs2: Array[ScoreDoc] = scoreDocs ++ scoreDocs1.filterNot(sd1 => scoreSet.contains(sd1.doc))

      // Exclude documents described in excludeIds
      val scoreDocs3: Array[ScoreDoc] = excludeIds match {
        case Some(eids) => scoreDocs2.filterNot(sd => eids.contains(sd.doc))
        case None => scoreDocs2
      }
      getIdScore(scoreDocs3, ngrams, analyzer, maxDocs, minNGrams2)
    }
  }

  /**
    * Uniform the characters, delete the small words, removes stopwords and limit the number of output words
    * @param text input text
    * @return set of words of the input text modified
    */
  private def uniformText(text: String): Set[String] = {
    Tools.strongUniformString(text)                            // uniform input string
      .split(" +")                                      // break the input string into tokens
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
    * @param lastDays filter documents whose 'update_date' is younger or equal to lastDays days
    * @param useDeCS if true DeCS synonyms will be added to the input text, if false the original input text will be used
    * @return the Lucene query object
    */
  private def getQuery(text: String,
                       sources: Option[Set[String]],
                       instances: Option[Set[String]],
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
    val queryInstances: Option[Query] = instances.map {
      set =>
        val builder: BooleanQuery.Builder = new BooleanQuery.Builder()
        set.foreach(insts => builder.add(new TermQuery(new Term("instance", insts)), BooleanClause.Occur.SHOULD))
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
    Seq(queryText, querySources, queryInstances, queryLastDays).flatten.foreach {
      qbuilder.add(_, BooleanClause.Occur.MUST)
    }
    qbuilder.build
  }

  /**
    * Get retrieved document's ids and scores filtering by number of common ngrams and then by update_date
    *
    * @param scoreDocs result of the Lucene search function
    * @param ngrams a set of ngrams generated from the input sreach text
    * @param analyzer Lucene analyzer
    * @param maxDocs maximum number of returned documents
    * @param minNGrams minimum number of common ngrams retrieved to consider returning a document
    * @return a list of pairs with Lucene document id and document score
    */
  private def getIdScore(scoreDocs: Array[ScoreDoc],
                         ngrams: Set[String],
                         analyzer: Analyzer,
                         maxDocs: Int,
                         minNGrams: Int): List[(Int, Float)] = {
    val ud: Set[String] = service.Conf.idxFldNames + "update_date"

    val tuple: Array[(Map[String, List[String]], ScoreDoc, String)] = scoreDocs.map {
      scoreDoc =>
        val doc: Map[String, List[String]] = loadDoc(scoreDoc.doc, ud)
        (doc, scoreDoc, doc.getOrElse("update_date", List("~")).headOption.getOrElse("~"))  // (doc, scoreDoc, update_date)
    }

    val timeSorted: Array[(Map[String, List[String]], ScoreDoc, String)] =
      tuple.sortWith((t1, t2) => t1._3.compareTo(t2._3) > 0)

    val idScore: List[(Int, Float)] = timeSorted.foldLeft[List[(Int, Float)]](List()) {
      case (lst, tuple) =>
        if (lst.size < maxDocs) {
          val docSet: Set[String] = tuple._1.foldLeft(Set[String]()) {
            case (set, kv) =>
              if (kv._1.equals("update_date")) set
              else set ++ kv._2
          }
          val docStr: String = docSet.mkString(" ")
          val simNGrams: Set[String] = getNGrams(docStr, analyzer, maxWords)
          val commonNGrams: Set[String] = ngrams.intersect(simNGrams)
          if (commonNGrams.size >= minNGrams) {
            //println(s"###>simNGrams=$simNGrams")
            //println(s"===> commonNGrams.size=${commonNGrams.size} commonNGrams=$commonNGrams")
            lst :+ (tuple._2.doc -> tuple._2.score)
          } // Filter by number of common ngrams
          else lst
        }
        else lst
    }
    idScore
  }

  /*
  private def getIdScore0(scoreDocs: Array[ScoreDoc],
                          ngrams: Set[String],
                          analyzer: Analyzer,
                          maxDocs: Int,
                          minNGrams: Int): List[(Int, Float)] = {
    val aux: Array[(Int, ScoreDoc, String)] = scoreDocs.map {   // (num of common ngrams, ScoreDoc, update_date)
      scoreDoc =>
        val doc: Map[String, List[String]] = loadDoc(scoreDoc.doc, service.Conf.idxFldNames + "update_date")
        val docStr: String = doc.foldLeft("") { case (str, (_, lst)) => str + " " + lst.mkString(" ") }
        val simNGrams: Set[String] = getNGrams(docStr, analyzer, maxWords)
        val commonNGrams: Set[String] = simNGrams.intersect(ngrams)
        (commonNGrams.size, scoreDoc, doc.getOrElse("update_date", List("~")).headOption.getOrElse("~"))
    }
    val min = Math.min(minNGrams, ngrams.size)

    // Filter by min common ngrams and then order by date and after order by number of common ngrams
    val lst: List[(Int, Float)] = aux.filter(t => t._1 >= min)
      .sortWith((t1, t2) => (t1._3 > t2._3) || ((t1._3 == t2._3) && (t1._1 > t2._1)))
      .take(maxDocs)
      .map(t => (t._2.doc, t._2.score))
      .toList

    lst
  }
  */

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

    asScalaBuffer[IndexableField](sdReader.document(id, fields.asJava).getFields()).
      foldLeft[Map[String,List[String]]] (Map()) {
      case (map2,fld) =>
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

    val tokenStream: TokenStream = analyzer.tokenStream(null, text)
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
  val docs: List[(Float,Map[String,List[String]])] = search.search(text, outFields, maxDocs, minNGrams, sources,
                                                                   instances, lastDays)

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
  println(s"Elapsed time2: ${new Date().getTime - startTime}")
}
