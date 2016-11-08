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
  doc_writer.commit()

  def close(): Unit = {
    doc_writer.close()
    doc_directory.close()
  }

  def newRecord(id: String): Int = {
    val doc_searcher = new IndexSearcher(doc_directory)
    val tot_docs = doc_searcher.search(new TermQuery(new Term("id", id)), 1)
    val total = if (tot_docs.totalHits == 0) {
      val doc = new Document()
      doc.add(new Field("id", id, Field.Store.YES, Field.Index.ANALYZED))
      doc.add(new Field("is_new", "true", Field.Store.YES,
                                          Field.Index.NOT_ANALYZED))
      doc.add(new NumericField("__total", Field.Store.YES, false).setIntValue(1))
      doc_writer.addDocument(doc)
      1
    } else {
      val doc = doc_searcher.doc(tot_docs.scoreDocs(0).doc)
      val tot = doc.getFieldable("__total").stringValue().toInt
      doc.removeField("__total")
      doc.add(new NumericField("__total", Field.Store.YES, false).
                                                           setIntValue(tot + 1))
      doc_writer.updateDocument(new Term("id", id), doc)
      tot + 1
    }
//println(s"newRecord total=$total")
    doc_writer.commit()
    doc_searcher.close()
    total
  }

  def deleteRecord(id: String): Unit = {
    doc_writer.deleteDocuments(new Term("id", id))
    doc_writer.commit()
  }

  def delRecIfUnique(id: String): Unit = {
    val doc_searcher = new IndexSearcher(doc_directory)
    val tot_docs = doc_searcher.search(new TermQuery(new Term("id", id)), 2)
//println(s"delRecIfUnique id=$id")
    if (tot_docs.totalHits > 0) {
      val term = new Term("id", id)
      val doc = doc_searcher.doc(tot_docs.scoreDocs(0).doc)
      val total = doc.getFieldable("__total").stringValue().toInt
      if (total == 1) doc_writer.deleteDocuments(term) else {
        doc.removeField("__total")
        doc.add(new NumericField("__total", Field.Store.YES, false).
                                                         setIntValue(total - 1))
        doc_writer.updateDocument(term, doc)
      }
    }
    doc_writer.commit()
    doc_searcher.close()
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
    val doc_searcher = new IndexSearcher(doc_directory)
    val topDocs = doc_searcher.search(new TermQuery(new Term("id", id)), 1)

    val total = if (topDocs.totalHits == 0) 1 else
      doc_searcher.doc(topDocs.scoreDocs(0).doc).getFieldable("__total").
                                                            stringValue().toInt
    val idSet = id.trim().split(" ").toList
    val (_,ids) = simDocs.searchPreProcessed(idSet, sd_analyzer, sdSearcher,
                                    idxFldName, freqSearcher, minMatch, maxDocs)
    val doc = new Document()
    doc.add(new Field("id", id, Field.Store.YES, Field.Index.ANALYZED))
    doc.add(new NumericField("__total", Field.Store.YES, false).setIntValue(total))
    ids.foreach(sd_id =>
      doc.add(new NumericField("sd_id", Field.Store.YES, false).
                                                            setIntValue(sd_id)))
    doc_writer.updateDocument(new Term("id", id), doc)
    doc_writer.commit()
    doc_searcher.close()
  }

  def updateNewRecordDocs(minMatch: Int = 3,
                          maxDocs: Int = 10): Unit = {
    val doc_searcher = new IndexSearcher(doc_directory)
    val topDocs = doc_searcher.search(
                             new TermQuery(new Term("is_new", "true")), 1000000)

    if (topDocs.totalHits > 0) {
      topDocs.scoreDocs.foreach (
        sdoc => {
          val doc = doc_searcher.doc(sdoc.doc)
          val id = doc.getFieldable("id").stringValue()
          updateRecordDocs(id, minMatch, maxDocs)
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
    (0 until max).filterNot(reader.isDeleted(_)).foreach (
      id => {
        val doc = doc_searcher.doc(id)
        val id2 = doc.getFieldable("id").stringValue()
        updateRecordDocs(id2, minMatch, maxDocs)
      }
    )
    doc_searcher.close()
  }
}
