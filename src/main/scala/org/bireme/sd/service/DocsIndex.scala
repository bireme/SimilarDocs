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
  val doc_directory = FSDirectory.open(Paths.get(docIndex))
  val doc_config = new IndexWriterConfig(lc_analyzer)
  val doc_writer =  new IndexWriter(doc_directory, doc_config)
  doc_writer.commit()

  /**
    * Closes all open resources
    */
  def close(): Unit = {
    doc_writer.close()
    doc_directory.close()
  }

  /**
    * Creates a new document if there is not one with this is. It there is
    * some, increment the total field
    *
    * @param id: document unique identifier
    * @return the total number of personal services documents associated with
    *         this one
    */
  def newRecord(id: String): Int = {
    val doc_reader = DirectoryReader.open(doc_writer)
    val doc_searcher = new IndexSearcher(doc_reader)
    val tot_docs = doc_searcher.search(new TermQuery(new Term("id", id)), 1)
    val total = if (tot_docs.totalHits == 0) { // there is a document with this id
      val doc = new Document()
      doc.add(new StringField("id", id, Field.Store.YES))
      doc.add(new StoredField("is_new", "true"))
      doc.add(new StoredField("__total", 1))
      doc_writer.addDocument(doc)
      1
    } else {                                // there is no document with this id
      val doc = doc_searcher.doc(tot_docs.scoreDocs(0).doc)
      val tot = doc.getField("__total").numericValue().intValue
      doc.removeField("__total")
      doc.add(new StoredField("__total", tot + 1))
      doc_writer.updateDocument(new Term("id", id), doc)
      tot + 1
    }
//println(s"newRecord total=$total")
    doc_writer.commit()
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
    def delDoc(): Unit = {
      val doc = new Document()
      doc.add(new StringField("id", id, Field.Store.YES))
      doc.add(new StoredField("is_new", "false"))
      doc.add(new StoredField("__total", 0))
      doc_writer.addDocument(doc)
      doc_writer.updateDocument(new Term("id", id), doc)
      doc_writer.commit()
    }

    val doc_reader = DirectoryReader.open(doc_writer)
    val doc_searcher = new IndexSearcher(doc_reader)
    val tot_docs = doc_searcher.search(new TermQuery(new Term("id", id)), 2)

    if (tot_docs.totalHits > 0) {
      if (onlyIfUnique) {
        val doc = doc_searcher.doc(tot_docs.scoreDocs(0).doc)
        val total = doc.getField("__total").numericValue().intValue
        if (total == 1) delDoc()

      } else delDoc()
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
                       minSim: Float,
                       maxDocs: Int = 10): Unit = {
    val doc_reader = DirectoryReader.open(doc_writer)
    val doc_searcher = new IndexSearcher(doc_reader)
    val topDocs = doc_searcher.search(new TermQuery(new Term("id", id)), 1)

    val total = if (topDocs.totalHits == 0) 0 else
      doc_searcher.doc(topDocs.scoreDocs(0).doc).getField("__total").
                                                         numericValue().intValue
    if  (total > 0) {
      val doc_searcher = new IndexSearcher(doc_reader)
      val results = simSearch.searchIds(id, idxFldNames, maxDocs, minSim)
      val doc = new Document()

      doc.add(new StringField("id", id, Field.Store.YES))
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
    * new records (having is_new field)
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
    *  records (new ones - having is_new field and old ones - with out field
    * is_new)
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
