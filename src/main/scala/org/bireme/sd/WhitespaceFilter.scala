package org.bireme.sd

import org.apache.lucene.analysis.{TokenFilter,TokenStream}
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute

import scala.collection.mutable.Queue

class WhitespaceFilter(input: TokenStream) extends TokenFilter(input) {
  private val termAtt = addAttribute(classOf[CharTermAttribute])
  private val queue = new Queue[String]()

  override def reset(): Unit = {
    super.reset()
    queue.clear()
  }

  override def incrementToken(): Boolean = {
//println(s"queue=$queue")
    if (queue.isEmpty)
      if (fillQueue()) setHeadToken()
      else false
    else setHeadToken()
  }

  private def setHeadToken(): Boolean = {
    termAtt.setEmpty()
    termAtt.append(queue.dequeue())
//println(s"setHeadToken termAtt=${termAtt.toString()}")
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
    queue ++= termAtt.toString().trim().split(" +")
    true
  }
}
