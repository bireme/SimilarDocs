package org.bireme.sd

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.core.{StopFilter,WhitespaceTokenizer}
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter
import org.apache.lucene.analysis.ngram.NGramTokenizer
import org.apache.lucene.analysis.Analyzer.TokenStreamComponents

class NGramAnalyzer(size: Int) extends Analyzer {
  override def createComponents(fieldName: String): TokenStreamComponents = {
     val source = new WhitespaceTokenizer()
     val filter1 = new UniformFilter(source) //new ASCIIFoldingFilter(source) 
     val filter2 = new StopFilter(filter1, Stopwords.getStopwords())
     val filter3 = new WhitespaceFilter(filter2)
     val filter4 = new NGramFilter(filter3, size)

     return new TokenStreamComponents(source, filter4)
   }

   /* override def createComponents(fieldName: String): TokenStreamComponents = {
      val source = new NGramTokenizer(size, size)
      val filter = new UniformFilter(source)

      return new TokenStreamComponents(source, filter)
    } */
}
