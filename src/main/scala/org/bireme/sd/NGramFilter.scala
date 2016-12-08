package org.bireme.sd

import org.apache.lucene.analysis.{TokenFilter,TokenStream}
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute

import scala.collection.mutable.Queue

class NGramFilter(input: TokenStream,
                  size: Int) extends TokenFilter(input) {
  private val termAtt = addAttribute(classOf[CharTermAttribute])
  private val queue = new Queue[String]()

  override def reset(): Unit = {
    super.reset()
    queue.clear()
  }

  override def incrementToken(): Boolean = {
    if (queue.isEmpty)
      if (fillQueue()) setHeadToken()
      else false
    else setHeadToken()
  }

  private def setHeadToken(): Boolean = {
    termAtt.setEmpty()
    termAtt.append(queue.dequeue())
    true
  }

  private def fillQueue(): Boolean = {
    def fillQueue(size: Int): Boolean = {
      if (size == 0) true
      else {
        if (input.incrementToken()) {
          splitAndFill()
          fillQueue(size - 1)
        } else !queue.isEmpty
      }
    }

    fillQueue(1000)
  }

  private def splitAndFill(): Boolean = {
    val buffer = termAtt.buffer()
    val len = termAtt.length()

    if (size > len) false
    else {
      (0 until (len / size)).foreach(pos => queue +=
                                           new String(buffer, pos * size, size))        
      if (len % size > 0) queue += new String(buffer, len - size, size)
      true
    }
  }
}
