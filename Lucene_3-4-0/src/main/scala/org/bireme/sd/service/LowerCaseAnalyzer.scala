package org.bireme.sd.service

import java.io.Reader

import org.apache.lucene.analysis.{Analyzer,KeywordTokenizer,LowerCaseFilter,TokenStream}
import org.apache.lucene.util.Version

import org.bireme.sd.UniformFilter

class LowerCaseAnalyzer(uniformTokens: Boolean = true) extends Analyzer {

  def tokenStream(fieldName: String,
                  reader: Reader): TokenStream = {
    val source = new KeywordTokenizer(reader)


    if (uniformTokens) new LowerCaseFilter(Version.LUCENE_34, new UniformFilter(
                                                  new KeywordTokenizer(reader)))
    else new LowerCaseFilter(Version.LUCENE_34, new KeywordTokenizer(reader))
  }
}
