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
  * totals: Number of personal services documents which have their profiles equal
  *         to this document id
  *
  * @author: Heitor Barbieri
  * date: 20170110
*/
class DocsIndex(docIndex: String,
                simSearch: SimDocsSearch) {
  val lc_analyzer = new LowerCaseAnalyzer(true)
  var doc_directory = FSDirectory.open(Paths.get(docIndex))
  var doc_writer =  new IndexWriter(doc_directory,
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
    * Forces the reopen of the IndexWriter
    */
  def refresh(): Unit = {
    close()

    val path = Paths.get(docIndex)

    // Force lock file deletion
    val file = new File(path.toFile(), "write.lock")
    if (file.isFile()) file.delete()

    doc_directory = FSDirectory.open(Paths.get(docIndex))
    doc_writer =  new IndexWriter(doc_directory,
                                  new IndexWriterConfig(lc_analyzer))
  }

  /**
    * Creates a new document if there is not one with this id. If there is
    * some, increment the '__total' field
    *
    * @param id: document unique identifier
    * @param idxFldNames: names of fields used to look for similar documents
    * @return the total number of personal services documents associated with
    *         this one
    */
  def newRecord(id: String,
                idxFldNames: Set[String]): Int = {
    val doc_reader = DirectoryReader.open(doc_writer)
    val doc_searcher = new IndexSearcher(doc_reader)
    val tot_docs = doc_searcher.search(new TermQuery(new Term("id", id)), 1)
    val total = if (tot_docs.totalHits == 0) { // there is no document with this id
      val doc = new Document()
      doc.add(new StringField("id", id, Field.Store.YES))
      doc.add(new StoredField("is_new", "true"))
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
    updateRecordDocs(id, idxFldNames)  // updates sd_id fields
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
      doc.add(new StoredField("is_new", if (isNew) "true" else "false"))
      sdIds.foreach(sdId => doc.add(new StoredField("sd_id", sdId)))
      doc.add(new StoredField("__total", total))
      if (isNew) doc_writer.addDocument(doc)
      else doc_writer.updateDocument(new Term("id", id), doc)
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
    * @return a list of Lucene ids of similar documents
    */
  def getDocIds(id: String): Set[Int] = {
    val doc_reader = DirectoryReader.open(doc_writer)
    val doc_searcher = new IndexSearcher(doc_reader)
    val topDocs = doc_searcher.search(new TermQuery(new Term("id", id)), 1)

    val retSet = if (topDocs.totalHits == 0) Set[Int]() else {
      val doc = doc_searcher.doc(topDocs.scoreDocs(0).doc)

      doc.getFields("sd_id").foldLeft[Set[Int]] (Set()) {
        case (set, fld) => set + fld.numericValue().intValue
      }
    }
    doc_reader.close()
    retSet
  }

  /**
    * Updates the sd_id (similar document ids) associated with this record id
    * (sentence)
    *
    * @param id document unique identifier
    * @param idxFldNames names of document fields used to find similar docs
    * @param minSim minimum acceptable similarity between documents
    * @param maxDocs maximum number of similar documents updateDocument
    */
  def updateRecordDocs(id: String,
                       idxFldNames: Set[String],
                       minSim: Float = 0.8f,
                       maxDocs: Int = 10): Unit = {
    val doc_reader = DirectoryReader.open(doc_writer)
    val doc_searcher = new IndexSearcher(doc_reader)
    val topDocs = doc_searcher.search(new TermQuery(new Term("id", id)), 1)

    val total = if (topDocs.totalHits == 0) 0 else
      doc_searcher.doc(topDocs.scoreDocs(0).doc).getField("__total").
                                                         numericValue().intValue
//println(s"TOTAL = $total")
    if  (total > 0) { // there are personal services documents with this profile
      val results = simSearch.searchIds(id, idxFldNames, maxDocs, minSim)
//println(s"results=$results")
      val doc = new Document()

      doc.add(new StringField("id", id, Field.Store.YES))
      doc.add(new StoredField("is_new", "false"))
      doc.add(new StoredField("__total", total))
      results.foreach {
        case (sd_id,_) => doc.add(new StoredField("sd_id", sd_id))
      }
      doc_writer.updateDocument(new Term("id", id), doc)
      doc_writer.commit()
    }
    doc_reader.close()
  }

  /**
    * Updates the sd_id (similar document ids) associated with all docIndex
    * new records (having is_new field equals to true)
    *
    * @param idxFldNames names of document fields used to find similar docs
    * @param minSim minimum acceptable similarity between documents
    * @param maxDocs maximum number of similar documents updateDocument
    */
  def updateNewRecordDocs(idxFldNames: Set[String],
                          minSim: Float,
                          maxDocs: Int = 10): Unit = {
    val doc_reader = DirectoryReader.open(doc_writer)
    val doc_searcher = new IndexSearcher(doc_reader)
    val topDocs = doc_searcher.search(
                             new TermQuery(new Term("is_new", "true")), 1000000)

    if (topDocs.totalHits > 0) {
      topDocs.scoreDocs.foreach (
        sdoc => {
          val doc = doc_searcher.doc(sdoc.doc)
          val id = doc.getField("id").stringValue()
          updateRecordDocs(id, idxFldNames, minSim, maxDocs)
        }
      )
    }
    doc_reader.close()
  }

  /**
    * Updates the sd_id (similar document ids) associated with all docIndex
    *  records: new ones - with is_new field equal to true and old ones -
    *  with out field is_new field equal to false
    *
    * @param idxFldNames names of document fields used to find similar docs
    * @param minSim minimum acceptable similarity between documents
    * @param maxDocs maximum number of similar documents updateDocument
    */
  def updateAllRecordDocs(idxFldNames: Set[String],
                          minSim: Float,
                          maxDocs: Int = 10): Unit = {
    val doc_reader = DirectoryReader.open(doc_writer)
    val doc_searcher = new IndexSearcher(doc_reader)
    val max = doc_reader.maxDoc
    (0 until max).foreach (
      id => {
        val doc = doc_searcher.doc(id)
        val total = doc.getField("__total").numericValue().intValue

        if (total > 0) {
          val id2 = doc.getField("id").stringValue()
          updateRecordDocs(id2, idxFldNames, minSim, maxDocs)
        }
      }
    )
    doc_reader.close()
  }
}
