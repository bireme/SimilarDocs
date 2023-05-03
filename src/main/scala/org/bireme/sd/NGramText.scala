/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/


package org.bireme.sd

import java.io.StringReader

import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.analysis.{Analyzer, TokenStream}

import scala.collection.immutable.TreeMap
import scala.util.{Failure, Success, Try}

/**
 * Object to convert an input text into a string composed by the max frequency ngrams generated from text.
  */
object NGramText {

  /**
    * Convert an input text into a string composed by the max frequency ngrams generated from text.
    * @param text input text
    * @param numOfTokens number of tokens (trigrams) that will form the output text
    * @return a text formed of the most frequent trigrams
    */
  def getNGramText(text: String,
                   numOfTokens: Int): Option[String] = {
    getTokenSet(text.trim, new NGramAnalyzer(NGSize.ngram_min_size, NGSize.ngram_max_size))
      .map(_.take(numOfTokens).map(tf => tf._1).mkString(" "))
  }

  /**
    * Given an input text generates a sequence of (token, frequency)
    * @param text input text
    * @param analyzer Lucene analyzer
    * @return sequence of (token, frequency)
    */
  private def getTokenSet(text: String,
                          analyzer: Analyzer): Option[Seq[(String, Int)]] = {
    val ts: TokenStream = analyzer.tokenStream("context", new StringReader(text))

    Try {
      ts.reset() // Resets this stream to the beginning. (Required)

      val map: Map[String, Int] = getFreq(ts, Map[String, Int]())
      val map2: Map[Int, String] = map.foldLeft(TreeMap[Int,String]()) {
        case (map3, (k, v)) => map3 + (v -> k)
      }
      map2.foldLeft(Seq[(String,Int)]()) {
        case (seq, (k,v)) => seq :+ (v -> k)
      }
    } match {
      case Success(seq) =>
        ts.end()
        ts.close()
        Some(seq)
      case Failure(_) =>
        ts.close()
        None
    }
  }

  /**
    * Given a TokenStream generates a map of (token -> frequency)
 *
    * @param ts Lucene TokenStream object
    * @param aux auxiliary map
    * @return a map of (token -> frequency)
    */
  @scala.annotation.tailrec
  private def getFreq(ts: TokenStream,
                      aux: Map[String, Int]): Map[String, Int] = {
    if (ts.incrementToken()) {
      val charTermAttribute: CharTermAttribute = ts.addAttribute(classOf[CharTermAttribute])
      val tok: String = charTermAttribute.toString
      val freq: Int  = aux.getOrElse(tok, 0)

      getFreq(ts, aux + (tok -> (freq  + 1)))
    } else aux
  }
}
