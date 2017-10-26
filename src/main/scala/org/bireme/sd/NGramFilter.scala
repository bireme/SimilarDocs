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

import org.apache.lucene.analysis.{TokenFilter,TokenStream}
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute

import scala.collection.mutable.Queue

/** Lucene filter that broken token stream into ngrams.
  *
  * @author: Heitor Barbieri
  * date: 20170102
  *
  * @param input the input token stream
  * @minSize the minimum ngram size
  * @maxSize the maximum ngram size
*/
class NGramFilter(input: TokenStream,
                  minSize: Int,
                  maxSize: Int) extends TokenFilter(input) {
  private val termAtt = addAttribute(classOf[CharTermAttribute])
  private val queue = new Queue[String]()

  /**
    * Cleans all internal buffers
    */
  override def reset(): Unit = {
    super.reset()
    queue.clear()
  }

  /**
    * Gets the next avalilable token
    *
    * @retun true if there is a next token, false otherwise
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
      if (qsize == 0) true
      else {
        if (input.incrementToken()) {
          splitAndFill()
          fillQueue(qsize - 1)
        } else !queue.isEmpty
      }
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
          pos => queue += new String(buffer, pos * maxTokSize, maxTokSize)
        }
        if (len % maxTokSize > 0)
          queue += new String(buffer, len - maxTokSize, maxTokSize)
        true
    }
  }
}
