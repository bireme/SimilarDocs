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

import collection.JavaConverters._
import java.io.File
import java.nio.file.Paths

import org.bireme.sd.SimDocsSearch

import org.apache.lucene.document.{Document, Field, StringField, StoredField}
import org.apache.lucene.index.{DirectoryReader, IndexWriter, IndexWriterConfig, Term}
import org.apache.lucene.search.{IndexSearcher, TermQuery}
import org.apache.lucene.store.FSDirectory

/** Collection of documents where each document has 3 fields:
  * id: Unique identifier. It is the expression used to look for similar
  *     documents in SDIndex (Similar Documents Lucene Index). This expression
  *     is also stored as a profile field in one or more documents of
  *     personal services document
  * sd_id: Repetitive field that contains the lucene id of a similar document
  * __total: Number of personal services documents which have their profiles equal
  *         to this document id
  * is_new: if 'true' the document is new and its sd_id fields should be searched,
  *         if 'false' the sd_id fields are updated
  *
  * @param docIndex Lucene index name
  * @param simSearch similar documents search engine
  * @param minSim minimum acceptable similarity between documents
  * @param maxDocs maximum number of similar documents updateDocument
  *
  * @author: Heitor Barbieri
  * date: 20170110
*/
class DocsIndex(docIndex: String,
                simSearch: SimDocsSearch,
                minSim: Float = Conf.minSim,
                maxDocs: Int = Conf.maxDocs) {
  val lc_analyzer = new LowerCaseAnalyzer(true)
  val doc_directory = FSDirectory.open(Paths.get(docIndex))
  val doc_writer =  new IndexWriter(doc_directory,
                                    new IndexWriterConfig(lc_analyzer))
  doc_writer.commit()

  /**
    * Closes all open resources
    */
  def close(): Unit = {
    doc_writer.close()
    doc_directory.close()
  }

  /**
    * @return  the IndexWriter of this objecto to the other classes inside this package.
    */
  private[service] def getIndexWriter(): IndexWriter = doc_writer

  /**
    * Creates a new document if there is not one with this id. If there is
    * some, increment the '__total' field
    *
    * @param id: document unique identifier
    * @return the total number of personal services documents associated with
    *         this one
    */
  def newRecord(id: String): Int = {
    val doc_reader = DirectoryReader.open(doc_writer)
    val doc_searcher = new IndexSearcher(doc_reader)
    val tot_docs = doc_searcher.search(new TermQuery(new Term("id", id)), 1) // searches skip deleted documents
    val total = if (tot_docs.totalHits == 0) { // there is no document with this id
      val doc = new Document()
      doc.add(new StringField("id", id, Field.Store.YES))
      doc.add(new StringField("is_new", "true", Field.Store.YES))
      doc.add(new StoredField("__total", 1))
      doc_writer.addDocument(doc)
      1
    } else {                                // there is a document with this id
      val doc = doc_searcher.doc(tot_docs.scoreDocs(0).doc)
      val tot = doc.getField("__total").numericValue().intValue
      doc.removeField("__total")
      doc.add(new StoredField("__total", tot + 1))
      doc_writer.updateDocument(new Term("id", id), doc)
      tot + 1
    }
    doc_writer.commit()
//println(s"newRecord total=$total")
    doc_reader.close()
    total
  }

  /**
    * Deletes a document with 'id' unique identifier by seeting the field
    * 'total' to zero if onlyIfUnique is not set or decrement it if it is set.
    *
    * @param id document unique identifier
    * @param onlyIfUnique if true decrements total, if false set it to zero.
    */
  def deleteRecord(id: String,
                   onlyIfUnique: Boolean = false): Unit = {
    def delDoc(total: Int,
               sdIds: Array[String],
               isNew: Boolean): Unit = {
      val doc = new Document()
      doc.add(new StringField("id", id, Field.Store.YES))
      doc.add(new StringField("is_new", if (isNew) "true" else "false",
                                                               Field.Store.YES))
      if (total > 0)
        sdIds.foreach(sdId => doc.add(new StoredField("sd_id", sdId)))
      doc.add(new StoredField("__total", total))
      doc_writer.updateDocument(new Term("id", id), doc)
      doc_writer.commit()
    }

    val doc_reader = DirectoryReader.open(doc_writer)
    val doc_searcher = new IndexSearcher(doc_reader)
    val tot_docs = doc_searcher.search(new TermQuery(new Term("id", id)), 1)

    if (tot_docs.totalHits > 0) {
      val doc = doc_searcher.doc(tot_docs.scoreDocs(0).doc)
      val isNew = "true".equals(doc.get("is_new"))
      val sdIds = doc.getValues("sd_id")

      if (onlyIfUnique) {
        val total = doc.getField("__total").numericValue().intValue
        if (total > 0) delDoc(total - 1, sdIds, isNew)
      } else delDoc(0, sdIds, isNew)
    }
    doc_reader.close()
  }

  /**
    * Retrieves a list of Lucene ids of similar documents associated with
    * this document id (sentence)
    *
    * @param id document unique identifier
    * @param idxFldNames names of document fields used to find similar docs
    * @return a list of Lucene ids of similar documents
    */
  def getDocIds(id: String,
                idxFldNames: Set[String]): Set[Int] = {
    val doc_reader = DirectoryReader.open(doc_writer)
    val doc_searcher = new IndexSearcher(doc_reader)
    val tid = new Term("id", id)
    val topDocs = doc_searcher.search(new TermQuery(tid), 1)

    val retSet = if (topDocs.totalHits == 0) Set[Int]() else {
      val doc = doc_searcher.doc(topDocs.scoreDocs(0).doc)
      if ("true".equals(doc.get("is_new"))) {  // document is new, update it with similar doc ids
        updateSdIds(doc, idxFldNames)
        doc_writer.updateDocument(tid, doc)
        doc_writer.commit()
      }
      doc.getFields("sd_id").foldLeft[Set[Int]] (Set()) {
        case (set, fld) =>
          val sd_id =  fld.numericValue().intValue
println(s"=> inserindo sd_id=$sd_id")
          set + sd_id
      }
    }
    doc_reader.close()
    retSet
  }

  /**
    * Updates the sd_id (similar document ids) associated with all docIndex
    * new records (having is_new field equals to true)
    *
    * @param idxFldNames names of document fields used to find similar docs
    */
  def updateNewRecordDocs(idxFldNames: Set[String]): Unit = {
    val doc_reader = DirectoryReader.open(doc_writer)
    val doc_searcher = new IndexSearcher(doc_reader)
    val topDocs = doc_searcher.search(
                            new TermQuery(new Term("is_new", "true")), 10000000)

    if (topDocs.totalHits > 0) {
      topDocs.scoreDocs.foreach (
        sdoc => {
          val doc = doc_searcher.doc(sdoc.doc)
          val id = doc.getField("id").stringValue()
          updateSdIds(doc, idxFldNames)
          doc_writer.updateDocument(new Term("id", id), doc)
        }
      )
      doc_writer.commit()
    }
    doc_reader.close()
  }

  /**
    * Updates the sd_id (similar document ids) associated with all docIndex
    *  records: new ones - with is_new field equal to true and old ones -
    *  with is_new field equal to false
    *
    * @param idxFldNames names of document fields used to find similar docs
    */
  def updateAllRecordDocs(idxFldNames: Set[String]): Unit = {
    val doc_reader = DirectoryReader.open(doc_writer)
    val doc_searcher = new IndexSearcher(doc_reader)
    val max = doc_reader.maxDoc
    (0 until max).foreach (
      luceneId => {
        val doc = doc_searcher.doc(luceneId)
        val total = doc.getField("__total").numericValue().intValue

        if (total > 0) {
          val id = doc.getField("id").stringValue()
          updateSdIds(doc, idxFldNames)
          doc_writer.updateDocument(new Term("id", id), doc)
        }
      }
    )
    doc_writer.commit()
    doc_reader.close()
  }

  /**
    * Deletes all sd_id (similar document ids) fields associated with this
    * document and set the 'is_new' flag to true
    */
  def changeAllDocsToNew(): Unit = {
    val doc_reader = DirectoryReader.open(doc_writer)
    val doc_searcher = new IndexSearcher(doc_reader)
    val max = doc_reader.maxDoc
    (0 until max).foreach (
      luceneId => {
        val doc = doc_searcher.doc(luceneId)
        val id = doc.getField("id").stringValue()

        doc.removeField("is_new")
        doc.removeFields("sd_id")
        doc.add(new StringField("is_new", "true", Field.Store.YES))

        doc_writer.updateDocument(new Term("id", id), doc)
      }
    )
    doc_writer.commit()
    doc_reader.close()
  }

  /**
    * Updates the sd_id (similar document ids) fields associated with this document
    *
    * @param doc Lucene document
    * @param idxFldNames names of document fields used to find similar docs
    */
  private[service] def updateSdIds(doc: Document,
                                   idxFldNames: Set[String]): Unit = {
println("entrando no updateSdIds")
    val id = doc.getField("id").stringValue()
    val total = doc.getField("__total").numericValue().intValue

    doc.removeField("is_new")
    doc.removeFields("sd_id")

    doc.add(new StringField("is_new", "false", Field.Store.YES))
println(s"total=$total")
    if (total > 0)
      simSearch.searchIds(id, idxFldNames, maxDocs, minSim).foreach {
        case (sd_id,_) => doc.add(new StoredField("sd_id", sd_id))
      }
  }
}
