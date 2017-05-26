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

import org.apache.lucene.document.{Document, Field, StringField, StoredField}
import org.apache.lucene.index.{DirectoryReader, IndexReader, IndexWriter, IndexWriterConfig, Term}
import org.apache.lucene.search.{IndexSearcher, MatchAllDocsQuery, TermQuery}
import org.apache.lucene.store.FSDirectory

object DocIndexRecreate extends App {

  private def usage(): Unit = {
    Console.err.println("usage: docIndexRecreate\n" +
      "\n\t-topIndexPath=<path> : top docs Lucene index path" +
      "\n\t-docIndexPath=<path> : doc Lucene index path"
    )
    System.exit(1)
  }

  if (args.length != 2) usage()
  recreateIndex(args(0), args(1))

  /**
    * Recreates the DocsIndex index from a TopIndex index.
    *
    * @param topIndexPath path/name of the TopIndex Lucene index
    */
  def recreateIndex(topIndexPath: String,
                    docIndexPath: String): Unit = {
    require((topIndexPath != null) && (!topIndexPath.trim.isEmpty))
    require((docIndexPath != null) && (!docIndexPath.trim.isEmpty))

    val docDirectory = FSDirectory.open(Paths.get(docIndexPath))
    val docWriter =  new IndexWriter(docDirectory,
                                new IndexWriterConfig(new LowerCaseAnalyzer()).
                                setOpenMode(IndexWriterConfig.OpenMode.CREATE))
    val docReader = DirectoryReader.open(docWriter)
    val docSearcher =  new IndexSearcher(docReader)
    val topDirectory = FSDirectory.open(Paths.get(topIndexPath))
    val topReader = DirectoryReader.open(topDirectory)
    val topSearcher = new IndexSearcher(topReader)
    val query = new MatchAllDocsQuery()
    val sdocs = topSearcher.search(query, Integer.MAX_VALUE).scoreDocs

    sdocs.foreach {
      case sdoc =>
        val doc = topSearcher.doc(sdoc.doc)
        doc.getFields().asScala.foreach {
          case fld =>
            val fname = fld.name
            println(s"+++ [$fname]")
            if (!fname.equals("id"))
              newRecord(fld.stringValue(), docSearcher, docWriter)
        }
    }
    topReader.close()
    docReader.close()
    docWriter.close()
  }

  /**
    * Creates a new document if there is not one with this id. If there is
    * some, increment the '__total' field
    *
    * @param id: document unique identifier
    * @return the total number of personal services documents associated with
    *         this one
    */
  private def newRecord(id: String,
                        docSearcher: IndexSearcher,
                        docWriter: IndexWriter): Int = {
    val totDocs = docSearcher.search(new TermQuery(new Term("id", id)), 1) // searches skip deleted documents
    val total = if (totDocs.totalHits == 0) { // there is no document with this id
      val doc = new Document()
      doc.add(new StringField("id", id, Field.Store.YES))
      doc.add(new StringField("is_new", "true", Field.Store.YES))
      doc.add(new StoredField("__total", 1))
      docWriter.addDocument(doc)
      1
    } else {                                // there is a document with this id
      val doc = docSearcher.doc(totDocs.scoreDocs(0).doc)
      val tot = doc.getField("__total").numericValue().intValue
      doc.removeField("__total")
      doc.add(new StoredField("__total", tot + 1))
      docWriter.updateDocument(new Term("id", id), doc)
      tot + 1
    }
    docWriter.commit()
    total
  }
}
