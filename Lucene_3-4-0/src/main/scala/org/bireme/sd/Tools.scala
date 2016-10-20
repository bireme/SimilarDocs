package org.bireme.sd

import scala.collection.JavaConversions._
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
  showDocuments(args(0))

  def showTerms(indexName: String): Unit = {
    val directory = FSDirectory.open(new File(indexName))
    val ireader = IndexReader.open(directory)
    val terms = ireader.terms()

    if (terms != null) {
      showTerms(terms)
      terms.close()
    }
    ireader.close()
    directory.close()
  }

  def showDocuments(indexName: String): Unit = {
    val directory = FSDirectory.open(new File(indexName))
    val ireader = IndexReader.open(directory)
    val last = ireader.maxDoc() - 1

    (0 to last).foreach {
      id => if (! ireader.isDeleted(id)) {
        println(s"======================= $id ========================")
        val doc = ireader.document(id)
        doc.getFields().foreach {
          fld => println(s"${fld.name()}: ${fld.stringValue()}")
        }
      }
    }
  }

  private def showIndexInfo(indexName: String): Unit = {
    val directory = FSDirectory.open(new File(indexName))
    val check = new CheckIndex(directory)

    println(s"info=${check.checkIndex().segmentFormat}")

    directory.close()
  }

  private def showTerms(terms: TermEnum): Unit = {
    val term = terms.term()
    if (term != null) println(s"[$term]")
    if (terms.next()) showTerms(terms)
  }
}
