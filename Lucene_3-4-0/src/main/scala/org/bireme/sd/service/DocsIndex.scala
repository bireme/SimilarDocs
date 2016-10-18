package org.bireme.sd.service

import collection.JavaConverters._
import java.io.File

import org.apache.lucene.document.{Document,Field, NumericField}
import org.apache.lucene.index.{IndexWriter,IndexWriterConfig, Term}
import org.apache.lucene.search.{IndexSearcher, TermQuery}
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.Version

import org.bireme.sd.{SDAnalyzer, SDTokenizer, SimilarDocs}

class DocsIndex(docIndex: String,
                sdSearcher: IndexSearcher,
                freqSearcher: IndexSearcher,
                idxFldName: Set[String]) {
  val sd_analyzer = new SDAnalyzer(SDTokenizer.defValidTokenChars, true)
  val lc_analyzer = new LowerCaseAnalyzer(true)
  val doc_directory = FSDirectory.open(new File(docIndex))
  val doc_config = new IndexWriterConfig(Version.LUCENE_34, lc_analyzer)
  val doc_writer =  new IndexWriter(doc_directory, doc_config)
  val simDocs = new SimilarDocs()

  def close(): Unit = {
    doc_writer.close()
    doc_directory.close()
  }

  def newRecord(id: String): Unit = {
    val doc_searcher = new IndexSearcher(doc_directory)
    val doc = new Document()

    doc.add(new Field("id", id, Field.Store.YES, Field.Index.ANALYZED))
    if (doc_searcher.search(new TermQuery(new Term("id", id)), 1).
                                                              totalHits == 0) {
      doc.add(new Field("is_new", "true", Field.Store.YES,
                                          Field.Index.NOT_ANALYZED))
      doc_writer.addDocument(doc)
      doc_writer.commit()
    }
    doc_searcher.close()
  }

  def deleteRecord(id: String): Unit = {
    doc_writer.deleteDocuments(new Term("id", id))
    doc_writer.commit()
  }

  def getDocIds(id: String): Set[Int] = {
    val doc_searcher = new IndexSearcher(doc_directory)
    val topDocs = doc_searcher.search(new TermQuery(new Term("id", id)), 1)

    val retSet = if (topDocs.totalHits == 0) Set[Int]() else {
      val doc = doc_searcher.doc(topDocs.scoreDocs(0).doc)

      doc.getFieldables("sd_id").foldLeft[Set[Int]] (Set()) {
        case (set, fld) => set + fld.stringValue().toInt
      }
    }
    doc_searcher.close()
    retSet
  }

  def updateRecordDocs(id: String,
                       minMatch: Int = 3,
                       maxDocs: Int = 10): Unit = {
    val doc = new Document()
    val (_,ids) = simDocs.search(id, sd_analyzer, sdSearcher, idxFldName,
                                               freqSearcher, minMatch, maxDocs)

    doc.add(new Field("id", id, Field.Store.YES, Field.Index.ANALYZED))
    ids.foreach(sd_id =>
            doc.add(new NumericField("sd_id", sd_id, Field.Store.YES, false)))

    doc_writer.updateDocument(new Term("id", id), doc)
    doc_writer.commit()
  }

  def updateNewRecordDocs(minMatch: Int = 3,
                          maxDocs: Int = 10): Unit = {
    val doc_searcher = new IndexSearcher(doc_directory)
    val topDocs = doc_searcher.search(
                             new TermQuery(new Term("is_new", "true")), 1000000)

    if (topDocs.totalHits > 0) {
      topDocs.scoreDocs.foreach(
        id => {
          val doc = doc_searcher.doc(id.doc)
          updateRecordDocs(doc.getFieldable("id").stringValue(), minMatch, maxDocs)
        }
      )
    }
    doc_searcher.close()
  }

  def updateAllRecordDocs(minMatch: Int = 3,
                          maxDocs: Int = 10): Unit = {
    val doc_searcher = new IndexSearcher(doc_directory)
    val reader = doc_searcher.getIndexReader()
    val max = reader.maxDoc
    (0 to max).filterNot(reader.isDeleted(_)).foreach(
      id => updateRecordDocs(doc_searcher.doc(id).getFieldable("id").
                                              stringValue(),  minMatch, maxDocs)
    )
    doc_searcher.close()
  }
}
