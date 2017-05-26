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
  val WAIT_TIME = 60000
  val query = new TermQuery(new Term("is_new", "true"))
  var running = false

  /** Start the update of sd_id fields of new documents until the stop function
    * is called
    */
  def start(): Unit = {
//println("###'start' function called")
    running = true

    Future {
      while (running) {
//println("### antes do 'updateOne()'")
        if (!updateOne()) {  // if there is not new document wait 1 minute
//println("### waiting for a new document !!!")
          Thread.sleep(WAIT_TIME)
        }
      }
    }
  }

  /** Stop the service
    */
  def stop(): Unit = {
    running = false
    Thread.sleep(WAIT_TIME)
  }

  /**
    * Update of sd_id fields of one new document
    *
    * @return true if a new document was found and updated and false otherwise
    */
  private def updateOne(): Boolean = {
//println("### entering 'updateOne' function")
    val indexWriter = docIndex.getIndexWriter()
//println("### step0")
    val indexReader = DirectoryReader.open(indexWriter)
//println("### step1")
    val indexSearcher = new IndexSearcher(indexReader)
//println("### step2")
    val topDocs = indexSearcher.search(query, 1)
//println("### step3")
    val found = (topDocs.totalHits > 0)
    if (found) { // There is a document with is_new flag setted
//println("### step4")
      val luceneId = topDocs.scoreDocs(0).doc
//println(s"### luceneId=$luceneId")
      val doc = indexSearcher.doc(luceneId)
      val id = doc.getField("id").stringValue()
//println(s"### step5 id=$id")
      docIndex.updateSdIds(doc, sd_idFldNames) // Updated sd_id fields
//println(s"### updating document")
      indexWriter.updateDocument(new Term("id", id), doc) // Update the document
//println("### step6")
      indexWriter.commit()
//println("### step7")
    }
    indexReader.close()
    found
  }
}
