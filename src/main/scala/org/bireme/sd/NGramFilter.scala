/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/


package org.bireme.sd

import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.analysis.{TokenFilter, TokenStream}

import scala.collection.mutable

/** Lucene filter that broken token stream into ngrams.
  *
  * author: Heitor Barbieri
  * date: 20170102
  *
  * @param input the input token stream
  * @param minSize the minimum ngram size
  * @param maxSize the maximum ngram size
*/
class NGramFilter(input: TokenStream,
                  minSize: Int,
                  maxSize: Int) extends TokenFilter(input) {
  private val termAtt: CharTermAttribute = addAttribute(classOf[CharTermAttribute])
  private val queue: mutable.Queue[String] = new mutable.Queue[String]()
  private val ngrams: mutable.Set[String] = mutable.Set[String]()  // Avoid duplicated ngram in the same field

  /**
    * Cleans all internal buffers
    */
  override def reset(): Unit = {
    super.reset()
    queue.clear()
    ngrams.clear()
  }

  /**
    * Gets the next avalilable token
    *
    * @return true if there is a next token, false otherwise
    */
  override def incrementToken(): Boolean = {
    if (queue.isEmpty)
      if (fillQueue()) setHeadToken()
      else false
    else setHeadToken()
  }

  /**
    * Sets the first token from buffer into the token stream
    *
    * @return true always
    */
  private def setHeadToken(): Boolean = {
    clearAttributes()
    termAtt.setEmpty()
    termAtt.append(queue.dequeue())
    /*val str = queue.dequeue()
    termAtt.append(str)
    println(s"token=[$str]")*/
    true
  }

  /**
    * Fills the buffer with new tokens
    *
    * @return true if there are tokens inside the buffer,
    * false otherwise
    */
  private def fillQueue(): Boolean = {
    def fillQueue(qsize: Int): Boolean = {
      if ((qsize > 0) && input.incrementToken()) {
        splitAndFill()
        fillQueue(qsize - 1)
      } else queue.nonEmpty
    }

    fillQueue(1000)
  }

  /**
    * Splits the input stream into ngrams and put all tokens
    * into the buffer
    *
    * @return true always
    */
  private def splitAndFill(): Boolean = {
    def maxTokenSize(bufferLen: Int,
                     tokSize: Int): Option[Int] = {
      if (bufferLen < minSize) None
      else if (bufferLen >= tokSize) Some(tokSize)
      else maxTokenSize(bufferLen, tokSize - 1)
    }

    val buffer = termAtt.buffer()
    val len = termAtt.length()

    maxTokenSize(len, maxSize) exists {
      maxTokSize =>
        (0 until (len / maxTokSize)).foreach {
          pos =>
            val ngram = new String(buffer, pos * maxTokSize, maxTokSize)
            if (ngrams.contains(ngram)) queue += ngram    // avoiding duplicated ngrams
            else ngrams += ngram
        }
        if (len % maxTokSize > 0) {
          val ngram = new String(buffer, len - maxTokSize, maxTokSize)
          if (ngrams.contains(ngram)) queue += ngram      // avoiding duplicated ngrams
          else ngrams += ngram
        }
        true
    }
  }
}
