/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.sd

import java.io.File

import org.apache.lucene.document.Document
import org.apache.lucene.index.{DirectoryReader, IndexReader}
import org.apache.lucene.search.{IndexSearcher, MatchAllDocsQuery, Query, ScoreDoc}
import org.apache.lucene.store.FSDirectory

//import collection.JavaConverters._
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

/** Iterator of Lucene documents
  *
  * author: Heitor Barbieri
  * date: 20190704
  *
  */
class DocumentIterator(ireader: IndexReader,
                       fieldsToLoad: Option[Set[String]]) extends Iterator[Document] {
  val query: Query = new MatchAllDocsQuery()
  val isearcher: IndexSearcher = new IndexSearcher(ireader)
  var scoreDocs: List[ScoreDoc] = fillDocuments(None)

  override def hasNext: Boolean = {
    if (scoreDocs.isEmpty) {
      isearcher.getIndexReader.close()
      false
    } else true
  }

  override def next(): Document = {
    val sdoc: ScoreDoc = scoreDocs match {
      case Nil => throw new NoSuchElementException()
      case h :: Nil =>
        scoreDocs = fillDocuments(Some(h))
        h
      case h :: t =>
        scoreDocs = t
        h
    }
    fieldsToLoad match {
      case Some(fields) => isearcher.storedFields.document(sdoc.doc, fields.asJava) //isearcher.doc(sdoc.doc, fields.asJava)
      case None => isearcher.storedFields.document(sdoc.doc)  // isearcher.doc(sdoc.doc)
    }
  }

  private def fillDocuments(after: Option[ScoreDoc]): List[ScoreDoc] = {
    Try (after match {
      case Some(aft) => isearcher.searchAfter(aft, query, 1000)
      case None => isearcher.search(query, 1000)
    }) match {
      case Success(topDoc) => topDoc.scoreDocs.toList
      case Failure(_) => Nil
    }
  }
}

object DocumentIterator extends App {
  private def usage(): Unit = {
    System.err.println("usage: DocumentIterator -index=<path> [-fields=<field>,...,<field>]")
    System.exit(1)
  }

  if (args.length < 1) usage()

  val parameters = args.foldLeft[Map[String,String]](Map()) {
    case (map,par) =>
      val split = par.split(" *= *", 2)
      map + ((split(0).substring(1), split(1)))
  }

  val directory: FSDirectory = FSDirectory.open(new File(parameters("index")).toPath)
  val ireader: DirectoryReader = DirectoryReader.open(directory)
  val fields: Option[Set[String]] = parameters.get("fields").map(_.split(" *, *").toSet)

  new DocumentIterator(ireader, fields).foreach(field => println(s"field=$field"))
}
