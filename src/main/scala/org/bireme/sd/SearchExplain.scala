/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.sd

import java.io.File
import org.apache.lucene.analysis.core.KeywordAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.index.{DirectoryReader, StoredFields}
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.{IndexSearcher, Query, ScoreDoc, TopDocs, TotalHits}
import org.apache.lucene.store.FSDirectory
import org.bireme.sd.service.Conf

import scala.collection.immutable.TreeMap
import scala.jdk.CollectionConverters._
import scala.util.Using

// dengue vacinação na cidade de São Paulo

object SearchExplain extends App {
  private def usage(): Unit = {
    Console.err.println("usage: SearchExplain <indexPath> <text>")
    System.exit(1)
  }

  if args.length < 2 then usage()

  private val dir = FSDirectory.open(new File(args(0)).toPath)

  Using.resource(DirectoryReader.open(dir)) { reader =>
    val searcher = new IndexSearcher(reader)
    val stored: StoredFields = searcher.storedFields()

    val sentence = Tools.uniformString(args(1))
    val tokens = getTokens(sentence)
    val words = sentence.trim.split(" +")

    println(s"\nSentence:\n${args(1)}")

    println("\nTokens:")
    tokens.foreach(tok => print(s"[$tok] "))

    println("\n\nHits:")
    val order = tokens.foldLeft[Map[Int, Set[String]]](TreeMap.empty) {
      case (map, tok) =>
        val hits = getTotalHits(searcher, tok)
        val values = map.getOrElse(hits, Set.empty[String])
        map + (hits -> (values + tok))
    }
    order.foreach { case (hits, toks) =>
      toks.foreach(value => println(s"[$value]: $hits"))
    }

    println("\nWord Hits:")
    val worder = words.foldLeft[Map[Int, Set[String]]](TreeMap.empty) {
      case (map, word) =>
        val hits = getWordTotalHits(searcher, word)
        val values = map.getOrElse(hits, Set.empty[String])
        map + (hits -> (values + word))
    }
    worder.foreach { case (hits, ws) =>
      ws.foreach { value =>
        val sum = getTokens(value).mkString(" OR ")
        if sum.isEmpty then println(s"[$value]: $hits")
        else println(s"[$value]=[$sum]: $hits")
      }
    }

    println("\nSentence Query:")
    println(s"[OR]:  ${getQuery(sentence, useOR = true)}")
    println(s"[AND]: ${getQuery(sentence, useOR = false)}")

    val OR  = getSentenceTotalHits(searcher, stored, sentence, useOR = true)
    val AND = getSentenceTotalHits(searcher, stored, sentence, useOR = false)

    println("\nSentence Hits:")
    println(s"[OR]: ${OR._1}")
    OR._2.foreach { case (doc, id, score) => println(s"\tdoc=$doc id=$id score=$score") }

    println(s"\n[AND]: ${AND._1}")
    AND._2.foreach { case (doc, id, score) => println(s"\tdoc=$doc id=$id score=$score") }
  }

  private def getTokens(text: String): Seq[String] = {
    val analyzer = new NGramAnalyzer(NGSize.ngram_min_size, NGSize.ngram_max_size)
    val tokenStream = analyzer.tokenStream("", text)
    val termAttr = tokenStream.addAttribute(classOf[CharTermAttribute])

    val out = new java.util.ArrayList[String]()
    tokenStream.reset()
    while tokenStream.incrementToken() do out.add(termAttr.toString)
    tokenStream.end()
    tokenStream.close()

    out.asScala.toSeq
  }

  // Lucene 10.3.2 idiomático: IndexSearcher.count(Query)
  private def getTotalHits(searcher: IndexSearcher, text: String): Int = {
    val analyzer = new KeywordAnalyzer()
    val parser = new QueryParser(Conf.indexedField, analyzer)
    val query = parser.parse(text)

    val count: Long = searcher.count(query)
    if count > Int.MaxValue then Int.MaxValue else count.toInt
  }

  private def getWordTotalHits(searcher: IndexSearcher, text: String): Int = {
    val analyzer = new NGramAnalyzer(NGSize.ngram_min_size, NGSize.ngram_max_size)
    val parser = new QueryParser(Conf.indexedField, analyzer)
    val query = parser.parse(text)

    val count: Long = searcher.count(query)
    if count > Int.MaxValue then Int.MaxValue else count.toInt
  }

  private def getSentenceTotalHits(
                                    searcher: IndexSearcher,
                                    stored: StoredFields,
                                    text: String,
                                    useOR: Boolean
                                  ): (Long, Seq[(Int, String, Float)]) = {
    val query: Query = getQuery(text, useOR)
    val topDocs: TopDocs = searcher.search(query, Conf.maxDocs)

    val totalHits: TotalHits = topDocs.totalHits
    val scoreDocs: Array[ScoreDoc] = topDocs.scoreDocs

    val docs = scoreDocs.foldLeft(Seq.empty[(Int, String, Float)]) { (seq, sc) =>
      val docId = sc.doc
      val id = stored.document(docId).get("id")
      seq :+ (docId, id, sc.score)
    }

    (totalHits.value, docs)
  }

  private def getQuery(text: String, useOR: Boolean): Query = {
    val analyzer = new NGramAnalyzer(NGSize.ngram_min_size, NGSize.ngram_max_size)
    val parser = new QueryParser(Conf.indexedField, analyzer)
    if !useOR then parser.setDefaultOperator(QueryParser.Operator.AND)
    parser.parse(text)
  }
}
