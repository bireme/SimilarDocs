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

import java.text.Normalizer
import java.text.Normalizer.Form

import org.apache.lucene.analysis.TokenFilter
import org.apache.lucene.analysis.TokenStream

import org.apache.lucene.analysis.tokenattributes.CharTermAttribute

/** Lucene filter that converts all letters into lower case,
  * converts graphical accents letters into normal ones a-z and
  * replaces all caracters that are not a-z 0-9 '_' and '-' into spaces
  *
  * @author: Heitor Barbieri
  * date: 20170102
  *
  * @param input the input token stream
*/
class UniformFilter(input: TokenStream) extends TokenFilter(input) {
  val termAtt: CharTermAttribute = addAttribute(classOf[CharTermAttribute])

  /**
    * Cleans all internal buffers
    */
  override def reset(): Unit = {
    super.reset()
    termAtt.setEmpty()
  }

  /**
    * Gets the next avalilable token
    *
    * @return true if there is a next token, false otherwise
    */
  override def incrementToken(): Boolean = {
    if (input.incrementToken()) {
      val str = uniformString(termAtt.toString())
      termAtt.setEmpty().append(str)
      true
    } else false
  }

  /**
    * Converts all input charactes into a-z, 0-9 '_' '-' and spaces
    *
    * @param in input string to be converted
    * @return the converted string
    */
  private def uniformString(in: String): String = {
    val s1 = Normalizer.normalize(in.toLowerCase(), Form.NFD)
    val s2 = s1.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")

    //s2.replaceAll("\\W", " ")
    s2.replaceAll("[^\\w\\-]", " ")  // Hifen
  }
}
