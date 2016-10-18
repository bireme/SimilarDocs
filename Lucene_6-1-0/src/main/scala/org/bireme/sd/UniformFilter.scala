package org.bireme.sd

import java.text.Normalizer
import java.text.Normalizer.Form

import org.apache.lucene.analysis.TokenFilter
import org.apache.lucene.analysis.TokenStream

import org.apache.lucene.analysis.tokenattributes.CharTermAttribute

class UniformFilter(input: TokenStream) extends TokenFilter(input) {
    val termAtt: CharTermAttribute = addAttribute(classOf[CharTermAttribute])

    override def incrementToken(): Boolean = {
      if (input.incrementToken()) {
        val str = uniformString(termAtt.toString()).toLowerCase()
        termAtt.setEmpty().append(str)
        true
      } else false
    }

    private def uniformString(in: String): String = {
      val s = Normalizer.normalize(in.toLowerCase(), Form.NFD)
      s.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
    }
}
