package org.bireme.sd

import ts.TimeString

import java.io.File
import java.util.GregorianCalendar

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.FSDirectory

import scala.collection.immutable.Set
import scala.io.Source

object SearchDocs extends App {
  private def usage(): Unit = {
    Console.err.println(
      "usage: SearchDocs -index=<path> -freqIndex=<path>" +
      "\n\t\t(-query_words=<word>,<word>, ... ,<word>|-query_file=<filePath>|" +
             "-query_url=<url>|-query_id=<docId>)" +
      "\n\t\t[-idxFldName=<name>,<name>,..,<name>] [-maxDocs=<val>]" +
      "\n\t\t[-queryEncoding=<str>] [-minMatchWords=<val>]" +
      "\n\t\t[--uniformToken]")
    System.exit(1)
  }

  private def readFileContent(path: String,
                              encoding: String): String = {
    val src = Source.fromFile(path, encoding)
    val content = src.mkString

    src.close()
    content
  }

  private def readUrlContent(url: String,
                             encoding: String): String = {
    val src = Source.fromURL(url, encoding)
    val content = src.mkString
    val title = """<title>(.+?)</title>""".r
    val h1 = """<h1>(.+?)</h1>""".r
    val h2 = """<h2>(.+?)</h2>""".r

    val s1 = title.findFirstMatchIn(content).map(_.group(1)).getOrElse("")
    val s2 = h1.findFirstMatchIn(content).map(_.group(1)).getOrElse("")

    src.close()
    s1 + " " + s2
  }

  if (args.length < 3) usage()

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
  val idxFldName0 = parameters.getOrElse("idxFldName", "idxField")
  val idxFldName = idxFldName0.split("\\,").toSet
  val maxDocs = parameters.getOrElse("maxDocs", "10").toInt
  val encoding = parameters.getOrElse("queryEncoding", "utf-8")
  val minMatch = parameters.getOrElse("minMatchWords", "3").toInt
  val uniform = parameters.contains("uniformToken")

  val analyzer = new SDAnalyzer(SDTokenizer.defValidTokenChars, uniform)
  val directory = FSDirectory.open(new File(index))
  val isearcher = new IndexSearcher(directory)
  val fdirectory = FSDirectory.open(new File(freqIndex))
  val fsearcher = new IndexSearcher(fdirectory)
  val simDocs = new SimilarDocs()

  val query = parameters.get("query_words") match {
    case Some(x) => x.trim.split(" *, *").mkString(" ")
    case None => parameters.get("query_file") match {
      case Some(w) => readFileContent(w, encoding)
      case None => parameters.get("query_url") match {
        case Some(y) => readUrlContent(y, encoding)
        case None => parameters.get("query_id") match {
          case Some(z) => simDocs.getWordsFromDoc(z, isearcher)
          case None => throw new IllegalArgumentException("query")
        }
      }
    }
  }

println(s"query=$query")
  val (words,ids) = simDocs.search(query, analyzer, isearcher, idxFldName,
                                   fsearcher, minMatch, maxDocs)
val ts = new TimeString()
ts.start()
  ids.foreach(id => println(simDocs.getDoc2(id, isearcher, idxFldName,
                                                  words.toSet) + "\n"))
println(s"getDocs=${ts.getTime}")
  directory.close()

  val totalTimeMili = new GregorianCalendar().getTimeInMillis() - beginTime
  println(s"Total time = ${totalTimeMili.toFloat / 1000} s")
}
