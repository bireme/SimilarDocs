/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/


package org.bireme.sd.service

import org.apache.lucene.analysis.{Analyzer,LowerCaseFilter}
import org.apache.lucene.analysis.core.KeywordTokenizer
import org.apache.lucene.analysis.Analyzer.TokenStreamComponents

import org.bireme.sd.UniformFilter

/** Lucene analyzer thar converts all tokens (words separated by white spaces)
  * to lower case ones
  */
class LowerCaseAnalyzer(uniformTokens: Boolean = true) extends Analyzer {

  /**
    * See Lucene Analyzer class documentation
    */
  override def createComponents(fieldName: String): TokenStreamComponents = {
    val source = new KeywordTokenizer()
    val filter = if (uniformTokens) new UniformFilter(source)
                 else new LowerCaseFilter(source)
    new TokenStreamComponents(source, filter)
  }
}
