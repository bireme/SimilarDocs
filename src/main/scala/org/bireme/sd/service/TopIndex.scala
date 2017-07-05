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

import java.nio.file.Paths
import java.text.Normalizer
import java.text.Normalizer.Form
import java.util.Date

import org.apache.lucene.document.{Document, Field, LongPoint, StringField, StoredField}
import org.apache.lucene.index.{DirectoryReader, IndexWriter, IndexWriterConfig, Term}
import org.apache.lucene.search.{IndexSearcher, TermQuery}
import org.apache.lucene.store.FSDirectory

import org.bireme.sd.SimDocsSearch

import scala.collection.JavaConverters._
import scala.collection.immutable.TreeSet

/** This class represents a personal service document that indexed by Lucene
  * engine. Each document has two kinds of fields:
  * id:  Unique identifier.
  * the other fields are profiles where the name of the field is the profile name
  * and its content is a sentence used to look for similar documents stored
  * at documents in SDIndex (Similar Documents Lucene Index).
  *
  * @param simSearch similar documents search engine object
  * @param topIndexPath path/name of the TopIndex Lucene index
  * @param idxFldNames names of document fields used to find similar docs
  *
  * @author: Heitor Barbieri
  * date: 20170601
*/
class TopIndex(simSearch: SimDocsSearch,
               topIndexPath: String,
               idxFldNames: Set[String] = Conf.idxFldNames) {
  require(simSearch != null)
  require((topIndexPath != null) && (!topIndexPath.trim.isEmpty))

  val idFldName = "id"                  // Profile identifier
  val userFldName = "user"              // Personal service user id
  val nameFldName = "prof_name"         // Profile name
  val contentFldName = "prof_content"   // Profile content
  val creationFldName = "creation_time" // Profile creation time
  val updateFldName = "update_time"     // Profile update time
  val sdIdFldName = "sd_id"             // Similar document Lucene doc id

  val lcAnalyzer = new LowerCaseAnalyzer(true)
  val topDirectory = FSDirectory.open(Paths.get(topIndexPath))
  val topWriter =  new IndexWriter(topDirectory,
                                   new IndexWriterConfig(lcAnalyzer))
  topWriter.commit()

  /**
    * Closes all open resources
    */
  def close(): Unit = {
    topWriter.close()
    topDirectory.close()
  }

  /**
    * Adds a profile instance to a personal services document
    *
    * @param user personal services document identifier
    * @param name profile name
    * @param content profile content
    */
  def addProfile(user: String,
                 name: String,
                 content: String): Unit = {
    require((user != null) && (!user.trim.isEmpty))
    require((name != null) && (!name.trim.isEmpty))
    require((content != null) && (!content.trim.isEmpty))

    val tuser = user.trim()
    val tname = name.trim()
    val id = s"${tuser}_${tname}"
    val updateTime = new Date().getTime()

    // Retrieves or creates the pesonal service document
    val (doc, isNew) = getDocuments(idFldName, id) match {
      case Some(lst) =>
        val doc2 = lst(0)
        doc2.removeField(idFldName)   // Avoid Lucene makes id tokenized (workarround)
        doc2.removeField(userFldName) // Avoid Lucene makes id tokenized (workarround)
        doc2.removeField(updateFldName)
        (doc2, false)
      case None =>
        val doc2 = new Document()
        doc2.add(new StoredField(nameFldName, tname))
        doc2.add(new StoredField(creationFldName, updateTime))
        (doc2, true)
    }

    // Add id and user fields
    doc.add(new StringField(idFldName, id, Field.Store.YES))
    doc.add(new StringField(userFldName, tuser, Field.Store.YES))

    // Add update_time field
    doc.add(new LongPoint(updateFldName, updateTime))
    doc.add(new StoredField(updateFldName, updateTime))

    // Add profile content field and similar docs id fields to the document
    addProfile(doc, content)

    // Saves document
    if (isNew) topWriter.addDocument(doc)
    else topWriter.updateDocument(new Term(idFldName, id), doc)
    topWriter.commit()
  }

  /**
    * Adds a profile instance to a personal services document. If there is
    * a profile with the same name, then replace it;
    *
    * @param doc personal services document
    * @param content profile content
    * @param minSim minimum acceptable similarity between documents
    * @param maxDocs maximum number of similar documents to be retrieved
    */
  private def addProfile(doc: Document,
                         content: String,
                         minSim: Float = Conf.minSim,
                         maxDocs: Int = Conf.maxDocs): Unit = {
    require(doc != null)
    require((content != null) && (!content.trim.isEmpty))

    val newContent = uniformString(content)
    val oldContent = doc.get(contentFldName)

    // Add profile field
    val getSdIds = if (oldContent == null) { // new profile
      doc.add(new StoredField(contentFldName, newContent))
      true
    } else { // there was already a profile with the same name
      if (oldContent.equals(newContent)) false
      else {  // same profile but with different sentence
        doc.removeField(contentFldName)  // only one occurrence for profile
        doc.add(new StoredField(contentFldName, newContent))
        true
      }
    }
    // Add similar documents ids
    if (getSdIds) {
      doc.removeFields(sdIdFldName)
      simSearch.searchIds(newContent, idxFldNames, maxDocs, minSim).foreach {
        case (id,_) => doc.add(new StoredField(sdIdFldName, id))
      }
    }
  }

  /**
    * Deletes all profiles from a personal services document
    *
    * @param user personal services document identifier
    */
  def deleteProfiles(user: String): Unit = {
    require((user != null) && (!user.trim.isEmpty))

   topWriter.deleteDocuments(new Term(userFldName, user.trim()))
   topWriter.commit()
  }

  /**
    * Deletes a profile from a personal services document
    *
    * @param user personal services document identifier
    * @param name profile name
    */
  def deleteProfile(user: String,
                    name: String): Unit = {
    require((user != null) && (!user.trim.isEmpty))
    require((name != null) && (!name.trim.isEmpty))

    val tuser = user.trim()
    val tname = name.trim()
    val id = s"${tuser}_${tname}"

    topWriter.deleteDocuments(new Term(idFldName, id))
    topWriter.commit()
  }

  /**
    * Given a personal services document, it returns all profiles contents
    * (some fields of that document) represented as a XML document
    *
    * @param user personal services document unique id
    * @return a XML document having profiles names and its contents. Profiles
    *         can have more than one occurrence
    */
  def getProfilesXml(user: String): String = {
    require((user != null) && (!user.trim.isEmpty))

    val head = """<?xml version="1.0" encoding="UTF-8"?><profiles>"""

    getProfiles(user).foldLeft[String](head) {
      case(str,(name,content)) =>
        s"""$str<profile><name>$name</name><content>$content</content></profile>"""
    } + "</profiles>"
  }

  /**
    * Given a personal services document, it returns all profiles contents
    * (some fields of that document)
    *
    * @param user personal services document unique id
    * @return a collection of profiles names and its contents. Profiles can not
    *         have more than one occurrence
    */
  def getProfiles(user: String): Map[String,String] = {
    require((user != null) && (!user.trim.isEmpty))

    val tUser = user.trim()

    getDocuments(userFldName, tUser) match {
      case Some(lst) => lst.foldLeft[Map[String,String]] (Map()) {
        case (map, doc) =>
          val name = doc.getField(nameFldName).stringValue()
          val content = doc.getField(contentFldName).stringValue()
          map + ((name, content))
      }
      case None => Map()
    }
  }

  /**
    * Given a id of a personal service document, profiles names and
    * similar documents fields where the profiles will be compared, returns
    * a list of similar documents represented as a XML document
    *
    * @param psId personal services document id
    * @param profiles name of profiles used to find similar documents
    * @param outFlds fields of similar documents to be retrieved
    * @param maxDocs the maximun number of similar documents to be retrieved
    * @return an XML document with each desired field and its respective
    *         occurrences, given that fields can have more than one occurrences
    */
  def getSimDocsXml(psId: String,
                    profiles: Set[String],
                    outFields: Set[String],
                    maxDocs: Int): String = {
    require((psId != null) && (!psId.trim.isEmpty))
    require(profiles != null)
    require(outFields != null)
    require(maxDocs > 0)

    val head = """<?xml version="1.0" encoding="UTF-8"?><documents>"""

    getSimDocs(psId, profiles, outFields, maxDocs).foldLeft[String] (head) {
      case (str,map) => {
        s"${str}<document>" + map.foldLeft[String]("") {
          case (str2, (tag,lst)) => {
            val tag2 = tag.trim().replaceAll(" +", "_")

            lst.size match {
              case 0 => str2
              case 1 => str2 + s"<$tag2>${cleanString(lst(0))}</$tag2>"
              case _ => str2 + lst.foldLeft[String]("") {
                case (str3,elem) => s"$str3<$tag2>${cleanString(elem)}</$tag2>"
              }
            }
          }
        } + "</document>"
      }
    } + "</documents>"
  }

  /**
    * Given a id of a personal service document, profiles names and
    * similar documents fields where the profiles will be compared, returns
    * a list of similar documents
    *
    * @param user personal services document id
    * @param names name of profiles used to find similar documents
    * @param outFlds fields of similar documents to be retrieved
    * @param maxDocs the maximun number of similar documents to be retrieved
    * @return a list of similar documents, where each similar document is a
    *         a collection of field names and its contents. Each fields can
    *         have more than one occurrence
    */
  def getSimDocs(user: String,
                 names: Set[String],
                 outFlds: Set[String],
                 maxDocs: Int): List[Map[String,List[String]]] = {
    require((user != null) && (!user.trim.isEmpty))
    require(names != null)
    require(outFlds != null)
    require(maxDocs > 0)

    val tuser = user.trim()
    val docIds = names.foldLeft[List[Set[Int]]](List()) {
      case (lst, name) =>
        val tname = name.trim()
        val id = s"${tuser}_${tname}"
        getDocuments(idFldName, id) match {
          case Some(lst2) =>
            lst :+ lst2(0).getFields(sdIdFldName).foldLeft[Set[Int]](Set()) {
              case (set, ifld) => set + ifld.numericValue().intValue()
            }
          case None => lst
        }
    }
    if (docIds.isEmpty) List()
    else {
      val sdDirectory = FSDirectory.open(Paths.get(simSearch.indexPath))
      val sdReader = DirectoryReader.open(sdDirectory)
      val sdSearcher = new IndexSearcher(sdReader)
      val list = limitDocs(docIds, maxDocs, List()).
                          foldLeft[List[Map[String,List[String]]]](List()) {
        case (lst, id) => {
          val fields = getDocFields(id, sdSearcher, outFlds)
          if (fields.isEmpty) lst else lst :+ fields
        }
      }
      sdReader.close()
      sdDirectory.close()
      list
    }
  }

  /**
    * Given a set of similar document identifiers for each profile, it
    * takes on id for each profile each time until the desired number of
    * ids has been reached.
    *
    * @param docs list of ids for each profile
    * @param maxDocs the maximum number of ids to be returned
    * @param ids auxiliary id list
    * @return a list of similiar document ids
    */
  private def limitDocs(docs: List[Set[Int]],
                        maxDocs: Int,
                        ids: List[Int]): List[Int] = {
    require(docs != null)
    require(maxDocs > 0)
    require(ids != null)

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

  /**
    * Retrieves the contents of some fields of the 'id' document
    *
    * @param id document identifier (personal service document identifier)
    * @param searcher Lucene IndexSearcher object. See Lucene documentation
    * @param fields set of field names whose content will be retrieved
    * @return a map of field name and it contents. A field can have more than
    *         one occurrence.
    */
  private def getDocFields(id: Int,
                           searcher: IndexSearcher,
                           fields: Set[String]): Map[String,List[String]] = {
    require(id >= 0)
    require(searcher != null)
    require(fields != null)

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

  /**
    * Retrieves Lucene Document objects given a field name and content
    *
    * @param field document field name
    * @param content document field content
    * @return probably a List of Lucene Document
    */
  private def getDocuments(field: String,
                           content: String): Option[List[Document]] = {
    require(field != null)
    require(content != null)

    val topReader = DirectoryReader.open(topWriter)
    val topSearcher = new IndexSearcher(topReader)
    val query = new TermQuery(new Term(field, content))

//println(s"query=$query")
    val docs = topSearcher.search(query, Integer.MAX_VALUE)
//println(s"totalHits=${docs.totalHits} id=[$id] query=[$query]")
    val result = docs.totalHits match {
      case 0 => None
      case _ => docs.scoreDocs.foldLeft[Option[List[Document]]] (Some(List[Document]())) {
        case (slst, sdoc) => slst.map(_ :+ topSearcher.doc(sdoc.doc))
      }
    }

    topReader.close()
    result
  }

  /**
    * Replaces some string characters ("&'<>) for their entity representation
    *
    * @param in the input string
    * @return the string with some characters replaced by entities
    */
  private def cleanString(in: String): String = {
    require(in != null)

    in.replace("\"", "&quot;").replace("&", "&amp;").replace("'", "&apos;").
       replace("<", "&lt;").replace(">", "&gt;")
  }

  /**
    * Converts all input charactes into a-z, 0-9 and spaces. Removes adjacent
    * whites and sort the words.
    *
    * @param in input string to be converted
    * @return the converted string
    */
  private def uniformString(in: String): String = {
    require(in != null)

    val s1 = Normalizer.normalize(in.toLowerCase(), Form.NFD)
    val s2 = s1.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")

    TreeSet(s2.replaceAll("\\W", " ").trim().split(" +"): _*).
                                             filter(_.length >= 3).mkString(" ")
  }

  /**
    * Update sdIdFldName fields of one document whose update time is outdated
    *
    * @param minSim minimum acceptable similarity between documents
    * @param maxDocs maximum number of similar documents to be retrieved
    * @return true if there was an update and false if not
    */
  def updateSimilarDocs(minSim: Float = Conf.minSim,
                        maxDocs: Int = Conf.maxDocs): Boolean = {
    val updateTime = new Date().getTime()
    val deltaTime =  (1000 * 60 * 60 * 2)  // 2 hours
    val query = LongPoint.newRangeQuery(updateFldName, 0, updateTime  - deltaTime) // all documents updated before one hour from now
    val topReader = DirectoryReader.open(topWriter)
    val topSearcher = new IndexSearcher(topReader)
    val topDocs = topSearcher.search(query, 1)
//println(s"###documentos a serem atualizados:${topDocs.totalHits} 0<=x<=${updateTime  - deltaTime}")

    // Update 'update time' field
    val retSet = if (topDocs.totalHits == 0) false else {
      val doc = topSearcher.doc(topDocs.scoreDocs(0).doc)
      doc.removeField(updateFldName)
      doc.add(new LongPoint(updateFldName, updateTime))
      doc.add(new StoredField(updateFldName, updateTime))

      // Lucene bug
      val id = doc.getField(idFldName).stringValue()
      doc.removeField(idFldName)
      doc.add(new StringField(idFldName, id, Field.Store.YES))

      // Lucene bug
      val user = doc.getField(userFldName).stringValue()
      doc.removeField(userFldName)
      doc.add(new StringField(userFldName, user, Field.Store.YES))

      // Include new similar doc id fields
      doc.removeFields(sdIdFldName)
      val content = doc.getField(contentFldName).stringValue()
      simSearch.searchIds(content, idxFldNames, maxDocs, minSim).foreach {
        case (sdId,_) => doc.add(new StoredField(sdIdFldName, sdId))
      }

      // Update document
      topWriter.updateDocument(new Term(idFldName, id), doc)
      topWriter.commit()
      true
    }
    topReader.close()
    retSet
  }
}
