package org.bireme.sd

import org.apache.lucene.analysis.util.CharTokenizer

class SDTokenizer(validTokenChars: Set[Char] = SDTokenizer.defValidTokenChars)
                                                         extends CharTokenizer {
  override def isTokenChar(c: Int): Boolean = validTokenChars.contains(c.toChar)
}

object SDTokenizer {
  val defValidTokenChars = ("0123456789abcdefghijklmnopqrstuvwxyz" +
    "ABCDEFGHIJKLMNOPQRSTUVWXYZçáéíóúàãẽõñäëöüâêôÇÁÉÍÓÚÀÃẼÕÑÂÊÔ_-").toSet
}
