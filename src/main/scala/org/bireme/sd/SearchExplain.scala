/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/


package org.bireme.sd

import java.io.File

import org.apache.lucene.analysis.core.KeywordAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.{IndexSearcher, Query, TotalHitCountCollector}
import org.apache.lucene.store.FSDirectory
import org.bireme.sd.service.Conf

import scala.collection.JavaConverters._
import scala.collection.immutable.TreeMap

// dengue vacinação na cidade de São Paulo

object SearchExplain extends App {
  private def usage(): Unit = {
    Console.err.println("usage: SearchExplain <indexPath> <text>")
    System.exit(1)
  }
  if (args.length < 2) usage()
  val dir = FSDirectory.open(new File(args(0)).toPath)
  val reader = DirectoryReader.open(dir)
  val searcher = new IndexSearcher(reader)
  val sentence = Tools.uniformString(args(1))
  val tokens = getTokens(sentence)
  val words = sentence.trim.split(" +")

  println(s"\nSentence:\n${args(1)}")

  println("\nTokens:")
  tokens.foreach(tok => print(s"[$tok] "))

  println("\n\nHits:")
  val order = tokens.foldLeft[Map[Int,Set[String]]](TreeMap()) {
    case (map,tok) =>
      val hits = getTotalHits(tok)
      val values = map.getOrElse(hits, Set[String]())
      map + (hits -> (values + tok))
  }
  order.foreach(kv => kv._2.foreach(value => println(s"[$value]: ${kv._1}")))

  println("\nWord Hits:")
  val worder = words.foldLeft[Map[Int,Set[String]]](TreeMap()) {
    case (map,word) =>
      val hits = getWordTotalHits(word)
      val values = map.getOrElse(hits, Set[String]())
      map + (hits -> (values + word))
  }
  worder.foreach {
    kv => kv._2.foreach {
      value =>
        val sum = getTokens(value).mkString(" OR ")
        if (sum.isEmpty) println(s"[$value]: ${kv._1}")
        else println(s"[$value]=[$sum]: ${kv._1}")
    }
  }

  println("\nSentence Query:")
  println(s"[OR]: ${getQuery(sentence, useOR = true)}")
  println(s"\n[AND]: ${getQuery(sentence, useOR = true)}")

  val OR = getSentenceTotalHits(sentence, useOR = true)
  val AND = getSentenceTotalHits(sentence, useOR = false)

  println("\nSentence Hits:")
  println(s"[OR]: ${OR._1}")
  OR._2.foreach {
    case (doc, id, score) => println(s"\tdoc=$doc id=$id score=$score")
  }
  println(s"\n[AND]: ${AND._1}")
  AND._2.foreach {
    case (doc, id, score) => println(s"\tdoc=$doc id=$id score=$score")
  }

  reader.close()

  private def getTokens(text: String): Seq[String] = {
    val analyzer = new NGramAnalyzer(NGSize.ngram_min_size, NGSize.ngram_max_size)
    val tokenStream = analyzer.tokenStream(null, text)
    val charTermAttribute = tokenStream.addAttribute(classOf[CharTermAttribute])
    val lst = new java.util.ArrayList[String]()

    tokenStream.reset()
    while (tokenStream.incrementToken()) lst.add(charTermAttribute.toString)
    lst.asScala
  }

  private def getTotalHits(text: String): Int = {
    val collector = new TotalHitCountCollector()
    val analyzer = new KeywordAnalyzer()
    val mqParser = new QueryParser(Conf.indexedField, analyzer)
    val query = mqParser.parse(text)

    searcher.search(query, collector)
    collector.getTotalHits
  }

  private def getWordTotalHits(text: String): Int = {
    val collector = new TotalHitCountCollector()
    val analyzer = new NGramAnalyzer(NGSize.ngram_min_size,
                                     NGSize.ngram_max_size)
    val mqParser = new QueryParser(Conf.indexedField, analyzer)
    val query = mqParser.parse(text)
    searcher.search(query, collector)
    collector.getTotalHits
  }

  private def getSentenceTotalHits(text: String,
                                   useOR: Boolean):
                                            (Long, Seq[(Int, String, Float)])= {
    val query =  getQuery(text, useOR)
    val topDocs = searcher.search(query, Conf.maxDocs)
    val totalHits = topDocs.totalHits
    val scoreDocs = topDocs.scoreDocs
    val docs = scoreDocs.foldLeft[Seq[(Int,String,Float)]](Seq()) {
      case (seq, sc) =>
        val doc = sc.doc
        val id = searcher.doc(doc).get("id")
        val score = sc.score

        seq :+ ((doc, id, score))
    }
    (totalHits.value, docs)
  }

  private def getQuery(text: String,
                       useOR: Boolean): Query = {
    val analyzer = new NGramAnalyzer(NGSize.ngram_min_size, NGSize.ngram_max_size)
    val mqParser = new QueryParser(Conf.indexedField, analyzer)
    if (!useOR) mqParser.setDefaultOperator(QueryParser.Operator.AND)

    mqParser.parse(text)
  }
}
