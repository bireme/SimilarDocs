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

import org.bireme.sd.service.Conf

import org.apache.lucene.analysis.core.KeywordAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.queryparser.classic.{MultiFieldQueryParser,QueryParser}
import org.apache.lucene.search.{IndexSearcher,Query,TotalHitCountCollector}
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.index.DirectoryReader

import scala.collection.JavaConverters._
import scala.collection.immutable.TreeMap

// dengue vacinação na cidade de São Paulo

object SearchExplain extends App {
  private def usage(): Unit = {
    Console.err.println("usage: SearchExplain <indexPath> <text> " +
      "[<field>,<field>,...,<field>]")
    System.exit(1)
  }
  if (args.size < 2) usage()
  val fields = if (args.size > 2) args(2).trim.split(" *\\, *").toSet
               else Conf.idxFldNames
  val dir = FSDirectory.open(new File(args(0)).toPath())
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
  println(s"[OR]: ${getQuery(sentence, true)}")
  println(s"\n[AND]: ${getQuery(sentence, true)}")

  val OR = getSentenceTotalHits(sentence, true)
  val AND = getSentenceTotalHits(sentence, false)

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
    while (tokenStream.incrementToken()) lst.add(charTermAttribute.toString())
    lst.asScala.toSeq
  }

  private def getTotalHits(text: String): Int = {
    val collector = new TotalHitCountCollector()
    val analyzer = new KeywordAnalyzer()
    val mqParser = new MultiFieldQueryParser(fields.toArray, analyzer)
    val query = mqParser.parse(text)

    searcher.search(query, collector)
    collector.getTotalHits
  }

  private def getWordTotalHits(text: String): Int = {
    val collector = new TotalHitCountCollector()
    val analyzer = new NGramAnalyzer(NGSize.ngram_min_size,
                                     NGSize.ngram_max_size)
    val mqParser = new MultiFieldQueryParser(fields.toArray, analyzer)
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

        seq :+ (doc, id, score)
    }
    (totalHits, docs)
  }

  private def getQuery(text: String,
                       useOR: Boolean): Query = {
    val analyzer = new NGramAnalyzer(NGSize.ngram_min_size, NGSize.ngram_max_size)
    val mqParser = new MultiFieldQueryParser(fields.toArray, analyzer)
    if (!useOR) mqParser.setDefaultOperator(QueryParser.Operator.AND)

    mqParser.parse(text)
  }
}
