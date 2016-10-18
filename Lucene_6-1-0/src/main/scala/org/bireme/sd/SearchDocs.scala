package org.bireme.sd

import java.nio.file.Paths
import java.util.GregorianCalendar

import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.FSDirectory

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.FSDirectory

import scala.collection.immutable.Set
import scala.io.Source

object SearchDocs extends App {
  private def usage(): Unit = {
    Console.err.println(
      "usage: SearchDocs -index=<path> -freqIndex=<path>" +
      "\n\t\t-query=(<word>,<word>, ... ,<word>|@<filePath>)]" +
      "\n\t\t[-idxFldName=<name>,<name>,..,<name>] [-maxDocs=<val>]" +
      "\n\t\t[-fileEncoding=<str>] [-minMatchWords=<val>]" +
      "\n\t\t[--uniformToken]")
    System.exit(1)
  }

  private def readContent(path: String,
                          encoding: String): String = {
    val src = Source.fromFile(path, encoding)
    val content = src.mkString

    src.close()
    content
  }

  if (args.length < 2) usage()

  val beginTime = new GregorianCalendar().getTimeInMillis()
  val parameters = args.foldLeft[Map[String,String]](Map()) {
    case (map,par) => {
      val split = par.split(" *= *", 2)
      if (split.length == 2) map + ((split(0).substring(1), split(1)))
      else map + ((split(0).substring(2), ""))
    }
  }
  val index = parameters("index")
  val freqIndex = parameters("freqIndex")
  val query0 = parameters("query")
  val idxFldName0 = parameters.getOrElse("idxFldName", "idxField")
  val idxFldName = idxFldName0.split("\\,").toSet
  val maxDocs = parameters.getOrElse("maxDocs", "10").toInt
  val encoding = parameters.getOrElse("fileEncoding", "utf-8")
  val query = if (query0(0) == '@') readContent(query0.substring(1), encoding)
              else query0.trim.split(" *, *").mkString(" ")
  val minMatch = parameters.getOrElse("minMatchWords", "3").toInt
  val uniform = parameters.contains("uniformToken")

  val analyzer = new SDAnalyzer(SDTokenizer.defValidTokenChars, uniform)
  val directory = FSDirectory.open(Paths.get(index))
  val ireader = DirectoryReader.open(directory)
  val isearcher = new IndexSearcher(ireader)
  val simDocs = new SimilarDocs()
  val (words,ids) = simDocs.search(query, analyzer, isearcher, idxFldName,
                                   freqIndex, minMatch, maxDocs)

  ids.foreach(id => println(simDocs.getDoc2(id, isearcher, idxFldName, words) +
                                                                     "\n"))
  ireader.close()
  directory.close()

  val totalTimeMili = new GregorianCalendar().getTimeInMillis() - beginTime
  println(s"Total time = ${totalTimeMili.toFloat / 1000} s")
}
