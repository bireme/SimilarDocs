/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/


package org.bireme.sd

import org.apache.lucene.analysis.TokenFilter
import org.apache.lucene.analysis.TokenStream

import org.apache.lucene.analysis.tokenattributes.CharTermAttribute

/** Lucene filter that converts all letters into lower case,
  * converts graphical accents letters into normal ones a-z and
  * replaces all characters that are not a-z 0-9 '_' and '-' into spaces
  *
  * author: Heitor Barbieri
  * date: 20170102
  *
  * @param inputTS the input token stream
*/
class UniformFilter(inputTS: TokenStream) extends TokenFilter(inputTS) {
  val termAtt: CharTermAttribute = addAttribute(classOf[CharTermAttribute])

  /**
    * Cleans all internal buffers
    */
  override def reset(): Unit = {
    super.reset()
    termAtt.setEmpty()
    ()
  }

  /**
    * Gets the next avalilable token
    *
    * @return true if there is a next token, false otherwise
    */
  override def incrementToken(): Boolean = {
    if (inputTS.incrementToken()) {
      val str = Tools.uniformString(termAtt.toString)
      clearAttributes()
      termAtt.setEmpty().append(str)
      true
    } else false
  }
}
