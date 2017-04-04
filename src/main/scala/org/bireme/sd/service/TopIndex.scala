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

import java.io.{File,IOException}
import java.nio.file.Paths
import java.text.Normalizer
import java.text.Normalizer.Form

import org.apache.lucene.analysis.core.KeywordAnalyzer
import org.apache.lucene.document.{Document, Field, StringField, StoredField}
import org.apache.lucene.index.{DirectoryReader, IndexWriter, IndexWriterConfig, Term}
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.{IndexSearcher,TermQuery,TopDocs}
import org.apache.lucene.store.FSDirectory

import org.bireme.sd.SimDocsSearch

import scala.collection.JavaConverters._
import scala.collection.immutable.TreeSet
import scala.io.Source
import scala.util.{Try, Success, Failure}

/** This class represents a personal service document that indexed by Lucene
  * engine. Each document has two kinds of fields:
  * id:  Unique identifier.
  * the other fields are profiles where the name of the field is the profile name
  * and its content is a sentence used to look for similar documents stored
  * at documents in SDIndex (Similar Documents Lucene Index).
  *
  * @author: Heitor Barbieri
  * date: 20170110
*/
class TopIndex(sdIndexPath: String,
               docIndexPath: String,
               topIndexPath: String,
               idxFldNames: Set[String] = Set(
  "ti","ti_pt","ti_ru","ti_fr","ti_de","ti_it","ti_en","ti_es","ti_eng","ti_Pt",
  "ti_Ru","ti_Fr","ti_De","ti_It","ti_En","ti_Es","ab_en","ab_es","ab_Es",
  "ab_de","ab_De","ab_pt","ab_fr","ab_french")) {

  val lcAnalyzer = new LowerCaseAnalyzer(true)
  val simSearch = new SimDocsSearch(sdIndexPath)
  val docIndex = new DocsIndex(docIndexPath, simSearch)

  var topDirectory = FSDirectory.open(Paths.get(topIndexPath))
  var topWriter =  new IndexWriter(topDirectory,
                                   new IndexWriterConfig(lcAnalyzer))
  topWriter.commit()

  /**
    * Forces the reopen of the IndexWriter
    */
  def refresh(): Unit = {
    close()
    val path = Paths.get(topIndexPath)

    // Force lock file deletion
    val file = new File(path.toFile(), "write.lock")
    if (file.isFile()) file.delete()

    topDirectory = FSDirectory.open(path)
    topWriter =  new IndexWriter(topDirectory,
                                 new IndexWriterConfig(lcAnalyzer))
    simSearch.refresh()
    docIndex.refresh()
  }

  /**
    * Closes all open resources
    */
  def close(): Unit = {
    topWriter.close()
    topDirectory.close()
    simSearch.close()
    docIndex.close()
  }

  /**
    * Adds profile instances to a personal services document
    *
    * @param psId personal services document identifier
    * @param profiles collection of profile name and content
    */
  def addProfiles(psId: String,
                  profiles: Map[String,String]): Unit = {
    val lpsId = psId.toLowerCase()

    // Retrieves or creates the pesonal service document
    val doc = getDocument(lpsId) match {
      case Some(doc) => doc
      case None => {
        val doc2 = new Document()
        doc2.add(new StringField("id", lpsId, Field.Store.YES))
        doc2
      }
    }
    // Adds profiles to the document
    profiles.foreach {
      case (name,sentence) => addProfile(doc, name, sentence)
    }
    // Saves document
    topWriter.updateDocument(new Term("id", lpsId), doc)
    topWriter.commit()
  }

  /**
    * Adds a profile instance to a personal services document
    *
    * @param psId personal services document identifier
    * @param name profile name
    * @param sentence profile content
    */
  def addProfile(psId: String,
                 name: String,
                 sentence: String): Unit = {
    val lpsId = psId.toLowerCase()

    // Retrieves or creates the pesonal service document
    val (doc,isNew) = getDocument(lpsId) match {
      case Some(doc) => (doc,false)
      case None =>
        val doc2 = new Document()
        doc2.add(new StringField("id", lpsId, Field.Store.YES))
        (doc2,true)
    }
    // Adds profile to the document
    addProfile(doc, name, sentence)

//doc.getFields().asScala.foreach(field => println(s"name:${field.name()} content:${field.stringValue()}"))
//println(s"addProfile psId=$psId doc=[$doc]")

    // Avoid Lucene makes id tokenized (workarround)
    doc.removeField("id")
    doc.add(new StringField("id", lpsId, Field.Store.YES))

    // Saves document
    if (isNew) topWriter.addDocument(doc)
    else topWriter.updateDocument(new Term("id", lpsId), doc)
    topWriter.commit()
  }

  /**
    * Adds a profile instance to a personal services document. If there is
    * a profile with the same name, then replace it;
    *
    * @param doc personal services document
    * @param name profile name
    * @param sentence profile content
    */
  private def addProfile(doc: Document,
                         name: String,
                         sentence: String): Unit = {
    val newSentence = uniformString(sentence)
    val oldSentence = doc.get(name)

    if (oldSentence == null) { // new profile
      doc.add(new StoredField(name, newSentence))
      docIndex.newRecord(newSentence, idxFldNames) // create a new document at docIndex
    } else { // there was already a profile with the same name
      if (! oldSentence.equals(newSentence)) { // same profile but with different sentence
        doc.removeField(name)  // only one occurrence for profile
        doc.add(new StoredField(name, newSentence))
        docIndex.deleteRecord(oldSentence, onlyIfUnique=true)
        docIndex.newRecord(newSentence, idxFldNames) // create a new document at docIndex
      }
    }
  }

  /**
    * Deletes all profiles from a personal services document
    *
    * @param psId personal services document identifier
    */
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

  /**
    * Deletes a profile from a personal services document
    *
    * @param psId personal services document identifier
    * @param name profile name
    * @return true if the profile was deleted or false if not
    */
  def deleteProfile(psId: String,
                    name: String): Boolean = {
    val lpsId = psId.toLowerCase()
    getDocument(lpsId) match {
      case Some(doc) => {
        if (deleteProfile(doc, name)) {
          // Avoid Lucene makes id tokenized (workarround)
          doc.removeField("id")
          doc.add(new StringField("id", lpsId, Field.Store.YES))

          topWriter.updateDocument(new Term("id", lpsId), doc)
          topWriter.commit()
          true
        } else false
      }
      case None => false
    }
  }

  /**
    * Deletes a profile from a personal services document
    *
    * @param doc personal services document
    * @param name profile name
    * @return true if the profile was deleted or false if not
    */
  private def deleteProfile(doc: Document,
                            name: String): Boolean = {
    val docId = doc.get(name)

    if (name.equals("id") || (docId == null)) false
    else {
      doc.removeField(name)
      docIndex.deleteRecord(docId, onlyIfUnique=true)
      true
    }
  }

  /**
    * Given a personal services document, it returns all profiles contents
    * (some fields of that document) represented as a XML document
    *
    * @param psId personal services document unique id
    * @return a XML document having profiles names and its contents. Profiles
    *         can have more than one occurrence
    */
  def getProfilesXml(psId: String): String = {
    val head = """<?xml version="1.0" encoding="UTF-8"?><profiles>"""

    getProfiles(psId).foldLeft[String](head) {
      case(str,(name,content)) =>
        s"""$str<profile><name>$name</name><content>$content</content></profile>"""
    } + "</profiles>"
  }

  /**
    * Given a personal services document, it returns all profiles contents
    * (some fields of that document)
    *
    * @param psId personal services document unique id
    * @return a collection of profiles names and its contents. Profiles can not
    *         have more than one occurrence
    */
  def getProfiles(psId: String): Map[String,String] = {
    val lpsId = psId.toLowerCase()

    getDocument(lpsId) match {
      case Some(doc) => doc.getFields.asScala.
        foldLeft[Map[String,String]] (Map()) {
          case (map, field) =>
            val id = field.name()
            if (id.equals("id")) map
            else map + ((id, field.stringValue()))
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
    * @param psId personal services document id
    * @param profiles name of profiles used to find similar documents
    * @param outFlds fields of similar documents to be retrieved
    * @param maxDocs the maximun number of similar documents to be retrieved
    * @return a list of similar documents, where each similar document is a
    *         a collection of field names and its contents. Each fields can
    *         have more than one occurrence
    */
  def getSimDocs(psId: String,
                 profiles: Set[String],
                 outFlds: Set[String],
                 maxDocs: Int): List[Map[String,List[String]]] = {
    val lpsId = psId.toLowerCase()

    getDocument(lpsId) match {
      case Some(doc) => {
        val docIds = getDocIds(doc, profiles)
        if (docIds.isEmpty) List()
        else {
          val sdReader = DirectoryReader.open(
                                       FSDirectory.open(Paths.get(sdIndexPath)))
          val sdSearcher = new IndexSearcher(sdReader)
          val list = limitDocs(docIds, maxDocs, List()).
                              foldLeft[List[Map[String,List[String]]]](List()) {
            case (lst, id) => {
              val fields = getDocFields(id, sdSearcher, outFlds)
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

  /**
    * Given a document and set of profile names, returns all of its contents
    * (personel services sentences)
    *
    * @param doc Lucene Document object
    * @param profiles a set of profiles names (fields of the given document)
    * @return a list with sets of contents of each profile. Profiles can have
    *         more than one occurrence
    */
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
    * Retrieves a Lucene Document object from personal services index,
    * given its identifier
    *
    * @param id document identifier (personal service document identifier)
    * @return Lucene Document
    */
  private def getDocument(id: String): Option[Document] = {
    val topReader = DirectoryReader.open(topWriter)
    val topSearcher = new IndexSearcher(topReader)
    val parser = new QueryParser("id", new KeywordAnalyzer())
    val query0 = parser.parse(id);
    val query = new TermQuery(new Term("id", id))
//println(s"query=$query")
    val docs = topSearcher.search(query, 1)
//println(s"totalHits=${docs.totalHits} id=[$id] query=[$query]")
    val result = docs.totalHits match {
      case 0 => None
      case _ => Some(topSearcher.doc(docs.scoreDocs(0).doc))
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
    in.replace("\"", "&quot;").replace("&", "&amp;").replace("'", "&apos;").
       replace("<", "&lt;").replace(">", "&gt;")
  }

  /**
    * Reads the content of a internet package
    *
    * @param url the address of the internet package
    * @param encoding the character enconding of the internet package
    * @return the page content or the error message
    */
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
    * Converts all input charactes into a-z, 0-9 and spaces. Removes adjacent
    * whites and sort the words.
    *
    * @param in input string to be converted
    * @return the converted string
    */
  private def uniformString(in: String): String = {
    val s1 = Normalizer.normalize(in.toLowerCase(), Form.NFD)
    val s2 = s1.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")

    TreeSet(s2.replaceAll("\\W", " ").trim().split(" +"): _*).
                                             filter(_.length >= 3).mkString(" ")
  }

/**
  * Prints all document fields.
  *
  * @param doc document whose fields will be printed
  */
  private def showDocFields(doc: Document): Unit = {
    doc.getFields.asScala.foreach {
      field => println(s"${field.name}:: ${field.stringValue}")
    }
  }
}
