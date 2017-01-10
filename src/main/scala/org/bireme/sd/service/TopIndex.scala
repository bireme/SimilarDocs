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

package org.bireme.sd.service

import java.io.IOException
import java.nio.file.Paths
import java.text.Normalizer
import java.text.Normalizer.Form


import org.apache.lucene.document.{Document, Field, StringField, StoredField}
import org.apache.lucene.index.{DirectoryReader, IndexWriter, IndexWriterConfig, Term}
import org.apache.lucene.search.{IndexSearcher, TermQuery}
import org.apache.lucene.store.FSDirectory

import org.bireme.sd.{SimDocsSearch}

import scala.collection.JavaConverters._
import scala.collection.immutable.TreeSet
import scala.io.Source
import scala.util.{Try, Success, Failure}

class TopIndex(sdIndexPath: String,
               docIndexPath: String,
               topIndexPath: String,
               idxFldName: Set[String] = Set("ti", "ab")) {
  val lcAnalyzer = new LowerCaseAnalyzer(true)
  val topDirectory = FSDirectory.open(Paths.get(topIndexPath))
  val topConfig = new IndexWriterConfig(lcAnalyzer)
  val topWriter =  new IndexWriter(topDirectory, topConfig)
  val docDirectory = FSDirectory.open(Paths.get(docIndexPath))
  val simSearch = new SimDocsSearch(sdIndexPath)
  val docIndex = new DocsIndex(docIndexPath, simSearch)

  topWriter.commit()

  def close(): Unit = {
    topWriter.close()
    topDirectory.close()
    docDirectory.close()
    simSearch.close()
    docIndex.close()
  }

  def addProfiles(psId: String,
                  profiles: Map[String,String],
                  genRelated: Boolean): Unit = {
    // Retrieves or creates the pesonal service document
    val doc = getDocument(psId) match {
      case Some(doc) => doc
      case None => {
        val doc2 = new Document()
        doc2.add(new StringField("id", psId, Field.Store.YES))
        doc2
      }
    }
    // Adds profiles to the document
    profiles.foreach {
      case (name,sentence) => addProfile(doc, name, sentence)
    }
    // Saves document
    topWriter.updateDocument(new Term("id", psId), doc)
    topWriter.commit()
  }

  def addProfile(psId: String,
                 name: String,
                 sentence: String): Unit = {
    // Retrieves or creates the pesonal service document
    val doc = getDocument(psId) match {
      case Some(doc) => doc
      case None => {
        val doc2 = new Document()
        doc2.add(new StringField("id", psId, Field.Store.YES))
        doc2
      }
    }
    // Adds profile to the document
    addProfile(doc, name, sentence)

    // Saves document
    topWriter.updateDocument(new Term("id", psId), doc)
    topWriter.commit()
  }

  private def addProfile(doc: Document,
                         name: String,
                         sentence: String): Unit = {
    val uSentence = uniformString(sentence)

    if (doc.getField(name) != null) {
      doc.add(new StoredField("name", uSentence))
      docIndex.newRecord(uSentence) // create a new document at docIndex
    }
  }

  def deleteProfiles(psId: String): Unit = {
    getDocument(psId) match {
      case Some(doc) => {
        doc.getFields().asScala.foreach(field => deleteProfile(doc, field.name()))
        topWriter.updateDocument(new Term("id", psId), doc)
        topWriter.commit()
      }
      case None => ()
    }
  }

  def deleteProfile(psId: String,
                    name: String): Unit = {
    getDocument(psId) match {
      case Some(doc) => {
        if (deleteProfile(doc, name)) {
          topWriter.updateDocument(new Term("id", psId), doc)
          topWriter.commit()
        }
      }
      case None => ()
    }
  }

  private def deleteProfile(doc: Document,
                            name: String): Boolean = {
    val size = doc.getFields().size()
    val docId = doc.get(name)
    if (name.equals("id") || (docId == null)) false
    else {
      doc.removeField(name)
      docIndex.deleteRecord(docId, onlyIfUnique=true)
      true
    }
  }

  def getProfilesXml(psId: String): String = {
    getProfiles(psId).foldLeft[String]("<profiles>") {
      case(str,(k,set)) =>
        s"""$str<profile><name>$k</name><words>${set.mkString(",")}</words></profile>"""
    } + "</profiles>"
  }

  def getProfiles(psId: String): Map[String,Set[String]] = {
    getDocument(psId) match {
      case Some(doc) => doc.getFields.asScala.
                                     foldLeft[Map[String,Set[String]]] (Map()) {
        case (map, field) =>
          val id = field.name()
          if (id.equals("id")) map
          else map + ((id, field.stringValue().split(" ").toSet))
      }
      case None => Map()
    }
  }

  def getSimDocsXml(psId: String,
                    profiles: Set[String],
                    outFields: Set[String],
                    maxDocs: Int): String = {
    val head = """<?xml version="1.0" encoding="UTF-8"?><documents>"""

    getSimDocs(psId, profiles, outFields, maxDocs).foldLeft[String] (head) {
      case (str,map) => {
        s"${str}<document>" + map.foldLeft[String]("") {
          case (str2, (tag,lst)) => {
            lst.size match {
              case 0 => str2
              case 1 => str2 + s"<$tag>${cleanString(lst(0))}</$tag>"
              case _ => str2 + s"<${tag}_list>" + lst.foldLeft[String]("") {
                case (str3,elem) => s"$str3<$tag>${cleanString(elem)}</$tag>"
              } + s"</${tag}_list>"
            }
          }
        } + "</document>"
      }
    } + "</documents>"
  }

  def getSimDocs(psId: String,
                 profiles: Set[String],
                 outFlds: Set[String],
                 maxDocs: Int): List[Map[String,List[String]]] = {
    getDocument(psId) match {
      case Some(doc) => {
        val docIds = getDocIds(doc, profiles)
//println(s"docIds=$docIds")
        if (docIds.isEmpty) List()
        else {
          val sdReader = DirectoryReader.open(
                                       FSDirectory.open(Paths.get(sdIndexPath)))
          val sdSearcher = new IndexSearcher(sdReader)
          val list = limitDocs(docIds, maxDocs, List()).
                              foldLeft[List[Map[String,List[String]]]](List()) {
            case (lst, id) => {
              val fields = getDocFields(id, sdSearcher, outFlds)
//println(s"fields=$fields")
              if (fields.isEmpty) lst else lst :+ fields
            }
          }
          sdReader.close()
          list
        }
      }
      case None => List()
    }
  }

  private def getDocIds(doc: Document,
                        profiles: Set[String]): List[Set[Int]] = {
    profiles.foldLeft[List[Set[Int]]](List()) {
      case (lst, id) => {
        val did = doc.getField(id)
        if (did == null) lst else {
          val ids = docIndex.getDocIds(did.stringValue())
          if (ids.isEmpty) lst else lst :+ ids
        }
      }
    }
  }

  private def limitDocs(docs: List[Set[Int]],
                        maxDocs: Int,
                        ids: List[Int]): List[Int] = {
    if (docs.isEmpty) ids.take(maxDocs)
    else {
      val num = maxDocs - ids.size
      if (num > 0) {
        val newIds = docs.foldLeft[Set[Int]](Set()) {
          case (outSet,lstSet) => if (lstSet.isEmpty) outSet
                                  else  outSet + lstSet.head
        }
        val newDocs = docs.foldLeft[List[Set[Int]]](List()) {
          case (lst,set) => if ((set.isEmpty)||(set.tail.isEmpty)) lst
                            else lst :+ set.tail
        }
        limitDocs(newDocs, maxDocs, (ids ++ newIds.take(num)))
      } else ids.take(maxDocs)
    }
  }

  private def getDocFields(id: Int,
                           searcher: IndexSearcher,
                           fields: Set[String]): Map[String,List[String]] = {
    val doc = searcher.doc(id)

    if (fields.isEmpty) { // put all fields
      doc.getFields().asScala.foldLeft[Map[String,List[String]]] (Map()) {
        case  (map, field) => {
          val name = field.name()
          val lst = map.getOrElse(name,List[String]())
          map + ((name, field.stringValue() :: lst))
        }
      }
    } else fields.foldLeft[Map[String,List[String]]] (Map()) {
      case  (map, field) => {
        val flds = doc.getFields(field)
        if (flds.isEmpty) map else {
          val lst = flds.foldLeft[List[String]](List()) {
            case (lst2, fld) => fld.stringValue() :: lst2
          }
          map + ((field, lst))
        }
      }
    }
  }

  private def getDocument(id: String): Option[Document] = {
    val topReader = DirectoryReader.open(topWriter)
    val topSearcher = new IndexSearcher(topReader)
    val docs = topSearcher.search(new TermQuery(new Term("id", id)), 1)

    val result = docs.totalHits match {
      case 0 => None
      case _ => Some(topSearcher.doc(docs.scoreDocs(0).doc))
    }

    topReader.close()
    result
  }

  private def cleanString(in: String): String = {
    in.replace("\"", "&quot;").replace("&", "&amp;").replace("''", "&pos;").
       replace("<", "&lt;").replace(">", "&gt;")
  }

  private def readUrlContent(url: String,
                             encoding: String): Try[String] = {
    val src = Try(Source.fromURL(url, encoding))

    src match {
      case Success(bsource) =>
        val content = bsource.mkString
        val title = """<title>(.+?)</title>""".r
        val h1 = """<h1>(.+?)</h1>""".r
        val s1 = title.findFirstMatchIn(content).map(_.group(1)).getOrElse("")
        val s2 = h1.findFirstMatchIn(content).map(_.group(1)).getOrElse("")
        val s3 = (s1 + " " + s2).trim

        bsource.close()
        if (s3.isEmpty) Failure(new IOException("empty title, h1 and h2"))
        else Success(s3)
      case Failure(x) => Failure(x)
    }
  }

  /**
    * Converts all input charactes into a-z, 0-9 and spaces
    *
    * @param in input string to be converted
    * @return the converted string
    */
  private def uniformString(in: String): String = {
    val s1 = Normalizer.normalize(in.toLowerCase(), Form.NFD)
    val s2 = s1.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")

    s2.replaceAll("\\W", " ")
  }
}
