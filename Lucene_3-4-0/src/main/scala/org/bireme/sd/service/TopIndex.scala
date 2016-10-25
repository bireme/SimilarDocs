package org.bireme.sd.service

import collection.JavaConverters._
import java.io.{File,IOException}

import org.apache.lucene.document.{Document, Field, Fieldable, NumericField}
import org.apache.lucene.index.{IndexWriter,IndexWriterConfig, Term}
import org.apache.lucene.search.{IndexSearcher, TermQuery}
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.Version

import org.bireme.sd.{SDAnalyzer, SDTokenizer, SimilarDocs}

import scala.collection.immutable.TreeSet
import scala.io.Source
import scala.util.{Try, Success, Failure}

class TopIndex(sdIndexPath: String,
               docIndexPath: String,
               freqIndexPath: String,
               topIndexPath: String,
               idxFldName: Set[String] = Set("ti", "ab")) {

  val sdAnalyzer = new SDAnalyzer(SDTokenizer.defValidTokenChars, true)
  val lcAnalyzer = new LowerCaseAnalyzer(true)
  val sdDirectory = FSDirectory.open(new File(sdIndexPath))
  val sdSearcher = new IndexSearcher(sdDirectory)
  val freqDirectory = FSDirectory.open(new File(freqIndexPath))
  val freqSearcher = new IndexSearcher(freqDirectory)
  val topDirectory = FSDirectory.open(new File(topIndexPath))
  val topConfig = new IndexWriterConfig(Version.LUCENE_34, lcAnalyzer)
  val topWriter =  new IndexWriter(topDirectory, topConfig)
  val docDirectory = FSDirectory.open(new File(docIndexPath))
  val docIndex = new DocsIndex(docIndexPath, sdSearcher, freqSearcher, idxFldName)
  val simDocs = new SimilarDocs()

  topWriter.commit()

  def delRecord(psId:String): Unit = {
    val topSearcher = new IndexSearcher(topDirectory)
    val topDocs = topSearcher.search(new TermQuery(new Term("id", psId)), 1)

    if (topDocs.totalHits != 0) {
      val doc = topSearcher.doc(topDocs.scoreDocs(0).doc)
       doc.getFieldables("doc_id").foreach {
        field => {
          val docId = field.stringValue()
          val topDocs = topSearcher.search(new TermQuery(
                                                  new Term("doc_id", docId)), 1)
          if (topDocs.totalHits == 1) docIndex.deleteRecord(docId)
        }
      }
      topWriter.deleteDocuments(new Term("id", psId))
      topWriter.commit()
    }
    topSearcher.close()
  }

  def close(): Unit = {
    sdSearcher.close()
    freqSearcher.close()
    topWriter.close()
    sdDirectory.close()
    freqDirectory.close()
    topDirectory.close()
    docDirectory.close()
    docIndex.close()
  }

  def addWords(psId: String,
               sentences: Set[String]): Unit = {
    val words = sentences.map(simDocs.getWordsFromString(_))
    addWords2(psId, words)
  }

  def addWords2(psId: String,
               words: Set[Set[String]]): Unit = {
    val words2:Set[String] = words.map(set => TreeSet(
                                       simDocs.getWords(set, freqSearcher): _*).
                                                    mkString(" ").toLowerCase())
    val topSearcher = new IndexSearcher(topDirectory)
    val doc = new Document()
    doc.add(new Field("id", psId, Field.Store.YES, Field.Index.NOT_ANALYZED))

    val topDocs = topSearcher.search(new TermQuery(new Term("id", psId)), 1)
    val isNew = (topDocs.totalHits == 0)
    if  (!isNew) {
      val docSearcher = new IndexSearcher(docDirectory)
      val doc = topSearcher.doc(topDocs.scoreDocs(0).doc)
      val oldDocs:Set[String] = doc.getFieldables("doc_id").
                                                       map(_.stringValue()).toSet
      (oldDocs &~ words2).foreach(did => {
        val topDocs2 = docSearcher.search(
                                        new TermQuery(new Term("id", did)), 1)
        if (topDocs2.totalHits == 1) docIndex.deleteRecord(did)
        else {} // Do nothing
      })
    }
    words2.foreach { wrds =>
      doc.add(new Field("doc_id", wrds, Field.Store.YES,
                                                     Field.Index.NOT_ANALYZED))
      docIndex.newRecord(wrds)
    }
    if (isNew) topWriter.addDocument(doc)
    else topWriter.updateDocument(new Term("id", psId), doc)

    topWriter.commit()
    topSearcher.close()
  }

  def getSimDocs(psId: String,
                 outFlds: Set[String],
                 maxDocs: Int = 10): List[Map[String,List[String]]] = {
    val topSearcher = new IndexSearcher(topDirectory)
    val topDocs = topSearcher.search(new TermQuery(new Term("id", psId)), 1)

    val list = if (topDocs.totalHits == 0) List() else {
      val doc = topSearcher.doc(topDocs.scoreDocs(0).doc)
      val docs = doc.getFieldables("doc_id")
      val docIds = docs.foldLeft[List[Set[Int]]] (List()) {
        case (lst,id) => {
          val ids = docIndex.getDocIds(id.stringValue())
          if (ids.isEmpty) lst else lst :+ ids
        }
      }
      if (docIds.isEmpty) List() else {
        limitDocs(docIds, maxDocs, List()).
                              foldLeft[List[Map[String,List[String]]]](List()) {
          case (lst, id) => {
            val fields = getDocFields(id, sdSearcher, outFlds)
            if (fields.isEmpty) lst else lst :+ fields
          }
        }
      }
    }
    topSearcher.close()
    list
  }

  def getSimDocsXml(psId: String,
                    outFields: Set[String]): String = {
    val head = """<?xml version="1.0" encoding="UTF-8"?>"<documents>""""

    getSimDocs(psId, outFields).foldLeft[String](head) {
      case (str,map) => {
        s"${str}<document>" + map.foldLeft[String]("") {
          case (str2, (tag,lst)) => {
            lst.size match {
              case 0 => str2
              case 1 => str2 + s"<$tag>${lst(0)}</$tag>"
              case _ => str2 + s"<${tag}_list>" + lst.foldLeft[String]("") {
                case (str3,elem) => s"$str3<$tag>$elem</$tag>"
              } + s"</${tag}_list>"
            }
          }
        } + "</document>"
      }
    } + "</documents>"
  }

  private def limitDocs(docs: List[Set[Int]],
                        maxDocs: Int,
                        ids: List[Int]): List[Int] = {
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
    } else if (num == 0) ids
    else ids.take(maxDocs)
  }

  private def getDocFields(id: Int,
                           searcher: IndexSearcher,
                           fields: Set[String]): Map[String,List[String]] = {
    val doc = searcher.doc(id)

    if (fields.isEmpty) { // put all fields
      List(doc.getFields()).foldLeft[Map[String,List[String]]](Map()) {
        case  (map, field:Fieldable) => {
          val name = field.name()
          val lst = map.getOrElse(name,List[String]())
          map + ((name, field.stringValue() :: lst))
        }
      }
    } else fields.foldLeft[Map[String,List[String]]](Map()) {
      case  (map, field) => {
        val flds = doc.getFieldables(field)
        if (flds.isEmpty) map else {
          val lst = flds.foldLeft[List[String]](List()) {
            case (lst2, fld) => fld.stringValue() :: lst2
          }
          map + ((field, lst))
        }
      }
    }
  }

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
        if (s3 isEmpty) Failure(new IOException("empty title, h1 and h2"))
        else Success(s3)
      case Failure(x) => Failure(x)
    }
  }
}