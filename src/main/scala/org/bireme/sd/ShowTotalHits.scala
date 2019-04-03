/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/


package org.bireme.sd

import java.io.File

import collection.JavaConverters._
import org.apache.lucene.index._
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.BytesRef

import scala.collection.immutable.TreeMap
import scala.util.{Failure, Success, Try}

object ShowTotalHits extends App {
  private def usage(): Unit = {
    Console.err.println("usage: ShowTotalHits <indexName>")
    System.exit(1)
  }

  if (args.length < 1) usage()

  showTotalHits(args(0))

  private def showTotalHits(index: String): Unit = {
    Try {
      val directory = FSDirectory.open(new File(index).toPath)
      val ireader = DirectoryReader.open(directory)

      countTerms(ireader) match {
        case Right(map) => map.foreach(kv => println(s"[${kv._1}]: ${kv._2}"))
        case Left(err) => Console.err.println(err)
      }
      ireader.close()
      directory.close()
    } match {
      case Success(_) => ()
      case Failure(exception) => Console.err.println(exception)
    }
  }

  private def countTerms(reader: IndexReader): Either[String, Map[String, Long]] = {
    Try {
      getFields(reader).foldLeft(TreeMap[String, Long]()) {
        case (map, field) =>
          val terms: Terms = MultiFields.getTerms(reader, field)
          //val terms: Terms = MultiTerms.getTerms(reader, field) Lucene 8.0.0

          if (terms == null) map
          else getTermsCount(terms.iterator(), map)
      }
    } match {
      case Success(map) => Right(map)
      case Failure(exception) => Left(exception.getMessage)
    }
  }

  private def getTermsCount(tum: TermsEnum,
                            aux: TreeMap[String, Long]): TreeMap[String, Long] = {
    val next: BytesRef = tum.next

    if (next == null) aux
    else {
      val term: String = next.utf8ToString()
      getTermsCount(tum, aux + (term -> (aux.getOrElse(term, 0L) + 1)))
    }
  }

  private def getFields(reader: IndexReader): Set[String] = {
    val finfos: FieldInfos = reader match {
      case lf: LeafReader => lf.getFieldInfos
      case _ => MultiFields.getMergedFieldInfos(reader)
      //case _ => FieldInfos.getMergedFieldInfos(reader) Lucene 8.0.0
    }
    finfos.asScala.map(fi => fi.name).toSet
  }
}
