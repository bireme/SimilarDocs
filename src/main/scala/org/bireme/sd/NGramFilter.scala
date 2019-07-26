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
    * Gets the next available token
    *
    * @return true if there is a next token, false otherwise
    */
  override def incrementToken(): Boolean = {
    if (queue.isEmpty)
      if (fillQueue()) getHeadToken
      else false
    else getHeadToken
  }

  /**
    * Get the top token from queue into the token stream
    *
    * @return true always
    */
  private def getHeadToken: Boolean = {
    clearAttributes()
    termAtt.setEmpty()
    if (queue.nonEmpty) termAtt.append(queue.dequeue())
    /*println(s"queue=$queue")
    val str = queue.dequeue()
    termAtt.append(str)
    println(s"token=[$str]")*/
    true
  }

  /**
    * Fills the buffer with new tokens
 *
    * @param maxqsize max number of tokens in the queue
    * @return true if there are tokens inside the buffer,
    * false otherwise
    */
  @scala.annotation.tailrec
  private def fillQueue(maxqsize: Int = 1000): Boolean = {
    if ((queue.size < maxqsize) && input.incrementToken()) {
      putOneNgram(maxSize, termAtt.buffer(), termAtt.length())
      fillQueue(maxqsize)
    } else queue.nonEmpty
  }

  /**
    * Take from the input stream the biggest ngram available and put it
    * into the buffer
    *
    * @param mtokSize max token size
    * @param buffer where the token will be extracted
    * @param bSize input buffer size
    */
  private def putOneNgram(mtokSize: Int,
                          buffer: Array[Char],
                          bSize: Int): Unit = {
    /***
      * @return the maximum token size that can be found in the remaining of the buffer
      */
    def maxTokenSize(rem: Int): Option[Int] = {
      if (rem < minSize) None
      else Some(Math.min(mtokSize, rem))
    }
//println(s"buffer=${buffer.toList}")
    maxTokenSize(bSize) foreach {
      maxTokSize =>
        val ngram = new String(buffer, 0, maxTokSize)
        if (!ngrams.contains(ngram)) {  // avoiding duplicated ngrams
          queue += ngram
          ngrams += ngram
        }
    }
  }
}
