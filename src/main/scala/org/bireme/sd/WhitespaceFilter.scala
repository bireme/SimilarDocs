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

import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.analysis.{TokenFilter, TokenStream}

import scala.collection.mutable

/** Lucene filter that split a token stream at white spaces.
  *
  * author: Heitor Barbieri
  * date: 20170102
  *
  * @param input the input token stream
*/
class WhitespaceFilter(input: TokenStream) extends TokenFilter(input) {
  private val termAtt = addAttribute(classOf[CharTermAttribute])
  private val queue = new mutable.Queue[String]()

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
    * @return true if there is a next token, false otherwise
    */
  override def incrementToken(): Boolean = {
//println(s"queue=$queue")
    clearAttributes()
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
//println(s"setHeadToken termAtt=${termAtt.toString()}")
    true
  }

  /**
    * Fills the buffer with new tokens
    *
    * @return true if there are tokens inside the buffer,
    * false otherwise
    */
  private def fillQueue(): Boolean = {
    def fillQueue(size: Int): Boolean = {
      if (size == 0) true
      else {
        if (input.incrementToken()) {
          splitAndFill()
          fillQueue(size - 1)
        } else queue.nonEmpty
      }
    }

    fillQueue(1000)
  }

  /**
    * Splits the input stream at white spaces and put all tokens
    * into the buffer
    *
    * @return true always
    */
  private def splitAndFill(): Boolean = {
    queue ++= termAtt.toString.trim().split(" +")
    true
  }
}
