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
    return new TokenStreamComponents(source, filter)
  }
}
