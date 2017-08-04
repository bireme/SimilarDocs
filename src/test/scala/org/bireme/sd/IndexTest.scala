package org.bireme.sd

import java.io.File

import org.apache.lucene.index.{DirectoryReader,IndexReader,Term}
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.search.TermQuery

import scala.collection.JavaConverters._
import scala.util.{Try, Success, Failure}

object IndexTest extends App {
  private def usage(): Unit = {
    Console.err.println("usage: IndexTest\n" +
      "\t\t<indexPath> - path to Lucene index\n" +
      "\t\t[<field>] - field name to be used in TermQuery. Default is 'ab'\n" +
      "\t\t[<term>] - term used to verify the number of hits. Default is 'dengue'")
    System.exit(0)
  }

  val size = args.size
  if (size < 1) usage()

  val field = if (size > 1) args(1) else "ab"
  val term = if (size > 2) args(2) else "dengue"

  val hits = Try[Int] {
    val directory = FSDirectory.open(new File(args(0)).toPath())
    val ireader = DirectoryReader.open(directory);
    val hitNum = checkDocs(new Term(field,term), ireader)

    ireader.close()
    directory.close()

    hitNum
  } match {
    case Success(h) => h
    case Failure(_) => 0
  }
//println(s"hits=$hits")
  sys.exit(hits)

  private def checkDocs(term: Term,
                        ireader: IndexReader): Int = {
    require(term != null)
    require(ireader != null)

    val numDocs = ireader.numDocs()
    if (numDocs <= 0) throw new Exception("numDocs <= 0")

    val query = new TermQuery(term)
    val isearcher = new IndexSearcher(ireader)
    val topDocs = isearcher.search(query, 1000)
    val totalHits = topDocs.totalHits

    if (totalHits <= 0) throw new Exception("totalHits <= 0")

    topDocs.scoreDocs.foreach {
      scoreDoc =>
        val doc = ireader.document(scoreDoc.doc)
        doc.getFields().asScala.map(_.stringValue())
    }

    totalHits
  }
}