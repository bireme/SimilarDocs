/*=========================================================================

    Copyright © 2017 BIREME/PAHO/WHO

    This file is part of SimilarDocs.

    SimilarDocs is free software: you can redistribute it and/or
    modify it under the terms of the GNU Lesser General Public License as
    published by the Free Software Foundation, either version 2.1 of
    the License, or (at your option) any later version.

    SimilarDocs is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public
    License along with SimilarDocs. If not, see <http://www.gnu.org/licenses/>.

=========================================================================*/

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
    }
    finfos.asScala.map(fi => fi.name).toSet
  }
}
