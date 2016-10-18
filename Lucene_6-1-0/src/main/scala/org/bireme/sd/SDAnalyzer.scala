package org.bireme.sd

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.Analyzer.TokenStreamComponents

class SDAnalyzer(validTokenChars: Set[Char] = SDTokenizer.defValidTokenChars,
                 uniformTokens: Boolean = true) extends Analyzer {

  override def createComponents(fieldName: String): TokenStreamComponents = {
      val source = new SDTokenizer(validTokenChars)

      if (uniformTokens) new TokenStreamComponents(source,
                                                   new UniformFilter(source))
      else new TokenStreamComponents(source)
   }
}
