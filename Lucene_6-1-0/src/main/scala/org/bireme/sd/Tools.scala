package org.bireme.sd

import java.io.File
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.TermsEnum
import org.apache.lucene.store.FSDirectory

object Tools extends App {
  private def usage(): Unit = {
    Console.err.println("usage: Tools <indexName> <fieldName>")
    System.exit(1)
  }

  if (args.length != 2) usage();

  showTerms(args(0), args(1))

  def showTerms(indexName: String,
                fieldName: String): Unit = {
    val directory = FSDirectory.open(new File(indexName).toPath())
    val ireader = DirectoryReader.open(directory);
    val leaves = ireader.leaves()

    if (!leaves.isEmpty()) {
      val terms = leaves.get(0).reader().terms(fieldName)
      if (terms != null) {
        getNextTerm(terms.iterator()).foreach(x => println(s"[$x]"))
      }
    }
    ireader.close()
    directory.close()
  }

  private def getNextTerm(terms: TermsEnum): Stream[String] = {
    if (terms == null) Stream.empty
    else {
      val next = terms.next()
      if (next == null) Stream.empty
      else next.utf8ToString() #:: getNextTerm(terms)
    }
  }
}
