package org.bireme.sd

import java.io.{Reader,StringReader}
import java.nio.file.Paths

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.FSDirectory

import org.apache.lucene.queries.mlt.{MoreLikeThis => MLikeT}

import scala.io.Source
import scala.collection.immutable.TreeMap

object MoreLikeThis extends App {
  private def usage(): Unit = {
    Console.err.println(
      "usage: MoreLikethis -index=<path> -query=(<word>,<word>, ... ,<word>|@<filePath>)]" +
      "\n\t\t[-idxFldName=<name>] [-maxDocs=<val>] [-fileEncoding=<str>] [--uniformToken]")
    System.exit(1)
  }

  if (args.length < 2) usage()

  val parameters = args.foldLeft[Map[String,String]](Map()) {
    case (map,par) => {
      val split = par.split(" *= *", 2)
      if (split.length == 2) map + ((split(0).substring(1), split(1)))
      else map + ((split(0).substring(2), ""))
    }
  }
  val index = parameters("index")
  val query0 = parameters("query")
  val idxFldName = parameters.getOrElse("idxFldName", "idxField")
  val maxDocs = parameters.getOrElse("maxDocs", "20").toInt
  val encoding = parameters.getOrElse("fileEncoding", "utf-8")
  val queryStr = if (query0(0) == '@') readContent(query0.substring(1), encoding)
                 else query0.trim.split(" *, *").mkString(" ")
  val uniform = parameters.contains("uniformToken")
  val analyzer = new SDAnalyzer(SDTokenizer.defValidTokenChars, uniform)

  val directory = FSDirectory.open(Paths.get(index))
  val ireader = DirectoryReader.open(directory)
  val isearcher = new IndexSearcher(ireader)
  val mlt = new MLikeT(ireader)
  mlt.setAnalyzer(analyzer)
  val target = new StringReader(queryStr)
  val query = mlt.like(idxFldName, target);
  val hits = isearcher.search(query, maxDocs);
  val ids = hits.scoreDocs.foldLeft[Set[Int]](Set()) {
    case (set, sd) => set + sd.doc
  }

  ids.foreach { _id => {
    val id = getDoc(_id, isearcher, "id")
    println(s"----------------------- $id -------------------------------")
    println(getDoc(_id, isearcher, idxFldName) + "\n")
  }}

  ireader.close()
  directory.close()

  private def getDoc(id: Int,
                     searcher: IndexSearcher,
                     idxFldName: String): String = {
    searcher.doc(id).get(idxFldName)
  }

  private def readContent(path: String,
                          encoding: String): String = {
    val src = Source.fromFile(path, encoding)
    val content = src.mkString

    src.close()
    content
  }
}
