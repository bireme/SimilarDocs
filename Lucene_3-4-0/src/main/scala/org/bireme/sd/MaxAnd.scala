package org.bireme.sd

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.queryParser.QueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.util.Version

import scala.util._

class MaxAnd(init: Set[String],
             minKeys: Int,
             analyzer: Analyzer,
             isearcher: IndexSearcher,
             idxFldName: String) {
  //println("-----------------------------------------------------------")
  //println(s"init=$init")
  val parser = new QueryParser(Version.LUCENE_34, idxFldName, analyzer)

  private def removeNoFindableWords(init: Set[String]): Set[String] = {
    //init.filter(word => !retrieveIds(word).isEmpty)
    init.filter(word => hasHits(word))
  }

  private def genAllExpressions(init: Set[String]): Stream[String] = {
    def getExprString(strSet: Set[String]): String = strSet.mkString(" AND ")

    def getExpressions(buffer: Set[Set[String]],
                       size: Int): Stream[String] = {
      //println("-----------------------------------------------------------")
      //println(s"buffer=$buffer size=$size minKeys=$minKeys")
      if (size < minKeys) Stream.empty
      else if (buffer.isEmpty) getExpressions(init.subsets(size - 1).toSet, size - 1)
      else {
        val expr = getExprString(buffer.head)
        //println("-----------------------------------------------------------")
        //println(buffer.head.size + " : " + expr)
        getExprString(buffer.head) #:: getExpressions(buffer.tail, size)
      }
    }

    val init2 = removeNoFindableWords(init)
    //println(s"findableWords=" + init2)
    getExpressions(Set(init2), init2.size)
  }

  private def hasHits(expr: String): Boolean = {
    val qry = parser.parse(expr)
    isearcher.search(qry, 1).totalHits > 0
  }

  private def retrieveIds(expr: String): Set[Int] = {
    val qry = parser.parse(expr)

    isearcher.search(qry, 1000).scoreDocs.foldLeft[Set[Int]](Set()) {
      case (set,score) => {
        //println("-----------------------------------------------------------")
        //println(s"expr=$expr doc=${score.doc}")
        set + score.doc
      }
    }
  }

  private def getNextId(expressions: Stream[String],
                        idBuffer: Set[Int],
                        goodIds: Set[Int]): Stream[(String,Int)] = {
    //println(s"getNextId idBuffer=$idBuffer goodIds=$goodIds\n")
    if (idBuffer.isEmpty) {
      if (expressions.isEmpty) Stream.empty else {
        val newIds = retrieveIds(expressions.head) -- goodIds
        //if (newIds.isEmpty) { print(s"[${expressions.head}]") } else {
          //println("-----------------------------------------------------------")
          println(s"getNextId - expr=${expressions.head} size=$newIds")
        //}
        getNextId(expressions.tail, newIds, goodIds ++ newIds)
      }
    } else (expressions.head,idBuffer.head) #::
                  getNextId(expressions, idBuffer.tail, goodIds)
  }

  def getIds(maxHits: Int): List[(String,Int)] = {
    if (maxHits > 0) {
      val all = genAllExpressions(init)
      println("all=[" + all + "]")
      getNextId(all, Set(), Set()).take(maxHits).toList
    } else List()
  }
}
