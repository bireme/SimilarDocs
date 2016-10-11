package org.bireme.sd

import java.io.File
import org.apache.lucene.index.{CheckIndex,IndexReader,TermEnum}
import org.apache.lucene.store.FSDirectory

object Tools extends App {
  private def usage(): Unit = {
    Console.err.println("usage: Tools <indexName>")
    System.exit(1)
  }

  if (args.length != 1) usage();

  showIndexInfo(args(0))
  showTerms(args(0))

  def showTerms(indexName: String): Unit = {
    val directory = FSDirectory.open(new File(indexName))
    val ireader = IndexReader.open(directory);
    val terms = ireader.terms()

    if (terms != null) {
      showTerms(terms)
      terms.close()
    }
    directory.close()
  }

  private def showIndexInfo(indexName: String): Unit = {
    val directory = FSDirectory.open(new File(indexName))
    val check = new CheckIndex(directory)

    println(s"info=${check.checkIndex().segmentFormat}")

    directory.close()
  }

  private def showTerms(terms: TermEnum): Unit = {
    val term = terms.term()
    if (terms.next()) {
      println(s"[$term]")
      showTerms(terms)
    }
  }
}
