/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/


package org.bireme.sd

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.core.{StopFilter,WhitespaceTokenizer}
import org.apache.lucene.analysis.Analyzer.TokenStreamComponents

/** Lucene analyzer that creates a token stream with tokens having
  * a specif length.
  * It brokens the input string at white charactes, removes accents and
  * no standard chars (a-z 0-9), removes stopwords and then creates the
  * tokens with a constant size.
  *
  * author: Heitor Barbieri
  * date: 20170102
  *
  * @param minSize the minimum size of the generated tokens
  * @param maxSize the maximum size of the generated tokens
*/
class NGramAnalyzer(minSize: Int,
                    maxSize: Int) extends Analyzer {
  override def createComponents(fieldName: String): TokenStreamComponents = {
     val source = new WhitespaceTokenizer()
     val filter1 = new UniformFilter(source) //new ASCIIFoldingFilter(source)
     val filter2 = new StopFilter(filter1, Stopwords.getStopwords)
     val filter3 = new WhitespaceFilter(filter2)
     val filter4 = new NGramFilter(filter3, minSize, maxSize)

     new TokenStreamComponents(source, filter4)
   }

   /* override def createComponents(fieldName: String): TokenStreamComponents = {
      val source = new NGramTokenizer(minSize, maxSize)
      val filter = new UniformFilter(source)

      return new TokenStreamComponents(source, filter)
    } */
}
