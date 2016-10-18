package org.bireme.sd

import java.io.Reader

import org.apache.lucene.analysis.CharTokenizer
import org.apache.lucene.util.Version

class SDTokenizer(reader: Reader,
                  validTokenChars: Set[Char] = SDTokenizer.defValidTokenChars)
                               extends CharTokenizer(Version.LUCENE_34,reader) {
  override def isTokenChar(c: Int): Boolean = validTokenChars.contains(c.toChar)
}

object SDTokenizer {
  val defValidTokenChars = ("0123456789abcdefghijklmnopqrstuvwxyz" +
    "ABCDEFGHIJKLMNOPQRSTUVWXYZçáéíóúàãẽõñäëöüâêôÇÁÉÍÓÚÀÃẼÕÑÂÊÔ_-").toSet
}
