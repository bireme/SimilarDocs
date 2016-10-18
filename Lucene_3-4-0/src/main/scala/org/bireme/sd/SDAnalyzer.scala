package org.bireme.sd

import java.io.Reader

import org.apache.lucene.analysis.{Analyzer,TokenStream}

class SDAnalyzer(validTokenChars: Set[Char] = SDTokenizer.defValidTokenChars,
                 uniformTokens: Boolean = true) extends Analyzer {

  def tokenStream(fieldName: String, 
                  reader: Reader): TokenStream = {
    if (uniformTokens) new UniformFilter(new SDTokenizer(reader, validTokenChars))
    else new SDTokenizer(reader, validTokenChars)
  }

 /*
  def reusableTokenStream(fieldName: String, 
                          reader: Reader): TokenStream = {
    val previous = getPreviousTokenStream().asInstanceOf[TokenStream]
    if (previous == null) {
      val tokenizer = if (uniformTokens) 
        new UniformFilter(new SDTokenizer(reader, validTokenChars))
      else new SDTokenizer(reader, validTokenChars)
      setPreviousTokenStream(tokenizer)
      tokenizer      
    } else {
      previous.reset(reader)
      previous 
    } 
  }*/
}
