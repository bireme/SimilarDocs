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

import org.apache.lucene.index.{DirectoryReader, Term}
import org.apache.lucene.search.{IndexSearcher, TermQuery}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/** Service that continuously updates all similar document ids (DocsIndex
*   document sd_id field
*
* @param docIndex DocsIndex objecto
* @param sd_idFldNames names of document fields used to find similar docs
* @author: Heitor Barbieri
* date: 20170524
*/
class UpdaterService(docIndex: DocsIndex,
                     sd_idFldNames: Set[String]) {
  val query = new TermQuery(new Term("is_new", "true"))
  var running = false

  /** Start the update of sd_id fields of new documents until the stop function
    * is called
    */
  def start(): Unit = {
    running = true

    Future {
      while (running) {
        if (!updateOne()) // if there is not new document wait 1 minute
println("### waiting for a new document !!!")
          //Thread.sleep(60000)
      }
    }
  }

  /** Stop the service
    */
  def stop(): Unit = {
    running = false
  }

  /**
    * Update of sd_id fields of one new document
    *
    * @return true if a new document was found and updated and false otherwise
    */
  private def updateOne(): Boolean = {
    val indexWriter = docIndex.getIndexWriter()
    val indexReader = DirectoryReader.open(indexWriter)
    val indexSearcher = new IndexSearcher(indexReader)
    val topDocs = indexSearcher.search(query, 1)

    val found = (topDocs.totalHits > 0)
    if (found) { // There is a document with is_new flag setted
      val luceneId = topDocs.scoreDocs(0).doc
      val doc = indexSearcher.doc(luceneId)
      val id = doc.getField("id").stringValue()

      docIndex.updateSdIds(doc, sd_idFldNames) // Updated sd_id fields
println(s"### updating document id=$id")
      indexWriter.updateDocument(new Term("id", id), doc) // Update the document
      indexWriter.commit()
    }
    indexReader.close()
    found
  }
}
