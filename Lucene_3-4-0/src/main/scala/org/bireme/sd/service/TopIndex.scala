package org.bireme.sd.service

import scala.collection.JavaConverters._
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

  def addProfiles(psId: String,
                  profiles: Map[String,String],
                  genRelated: Boolean): Unit = {
    val profs = profiles.map {
      case (id, sentence) => (id, simDocs.getWordsFromString(sentence))
    }
    addProfiles2(psId, profs, genRelated)
  }

  def addProfiles2(psId: String,
                   profiles: Map[String,Set[String]],
                   genRelated: Boolean): Unit = {
    val doc = getDocument(psId, topDirectory) match {
      case Some(doc) => doc
      case None => {
        val doc2 = new Document()
        doc2.add(new Field("id", psId, Field.Store.YES,
                                                      Field.Index.NOT_ANALYZED))
        doc2
      }
    }
    profiles.foreach {
      case (id, words) =>
        val words2 = TreeSet(simDocs.getWords(words, freqSearcher): _*)
        addProfile(doc, id, words2, genRelated)
    }
    topWriter.updateDocument(new Term("id", psId), doc)
    topWriter.commit()
  }

  def addProfile(psId: String,
                 id: String,
                 sentence: String,
                 genRelated: Boolean = true): Unit = {
    val words = simDocs.getWordsFromString(sentence)
    addProfile(psId, id, words, genRelated)
  }

  def addProfile(psId: String,
                 id: String,
                 words: Set[String],
                 genRelated: Boolean): Unit = {
    getDocument(psId, topDirectory) match {
      case Some(doc) => {
        addProfile(doc, id, words, genRelated)
        topWriter.updateDocument(new Term("id", psId), doc)
      }
      case None => {
        val doc = new Document()
        doc.add(new Field("id", psId, Field.Store.YES, Field.Index.NOT_ANALYZED))
        addProfile(doc, id, words, genRelated)
        topWriter.addDocument(doc)
      }
    }
    topWriter.commit()
  }

  private def addProfile(doc: Document,
                         id: String,
                         words: Set[String],
                         genRelated: Boolean): Unit = {
println("\ninputed words:" + words)
words.foreach(w => s"\t$w")
    //val filteredWords = TreeSet(simDocs.getWords(words, freqSearcher): _*)
    val filteredWords = TreeSet(simDocs.getWords(words, idxFldName,
                    sdSearcher.getIndexReader(), simDocs.MAX_PROCESS_WORDS): _*)
    val filteredWordsStr = filteredWords.mkString(" ")
    val field = doc.getFieldable(id)
    if (field != null) {
      val did = field.stringValue()
      if (did != null) docIndex.delRecIfUnique(did)
      doc.removeField(id)
      doc.removeField(id + "__original")
    }
    doc.add(new Field(id, filteredWordsStr, Field.Store.YES,
                                                      Field.Index.NOT_ANALYZED))
    doc.add(new Field(id + "__original", words.mkString(" "), Field.Store.YES,
                                                      Field.Index.NOT_ANALYZED))
    if (genRelated) docIndex.updateRecordDocs(filteredWordsStr)
    else docIndex.newRecord(filteredWordsStr)
  }

  def deleteProfiles(psId: String): Unit = {
    getDocument(psId, topDirectory) match {
      case Some(doc) => {
        doc.getFields().asScala.foreach(field => deleteProfile(doc, field.name()))
        topWriter.deleteDocuments(new Term("id", psId))
        topWriter.commit()
      }
      case None => ()
    }
  }

  def deleteProfile(psId: String,
                    profId: String): Unit = {
    getDocument(psId, topDirectory) match {
      case Some(doc) => {
        if (deleteProfile(doc, profId))
          topWriter.deleteDocuments(new Term("id", psId))
        else topWriter.updateDocument(new Term("id", psId), doc)
        topWriter.commit()
      }
      case None => ()
    }
  }

  private def deleteProfile(doc: Document,
                            id: String): Boolean = {
    val size = doc.getFields().size()
    val otherId = doc.get(id)
    if (id.equals("id") || (otherId == null)) {
      size == 1
    } else {
      doc.removeField(id)
      docIndex.delRecIfUnique(otherId)
      (size - 1) == 1
    }
  }

  def getProfilesXml(psId: String): String = {
    getProfiles(psId).foldLeft[String]("<profiles>") {
      case(str,(k,set)) =>
        s"""$str<profile><name>$k</name><words>${set.mkString(",")}</words></profile>"""
    } + "</profiles>"
  }

  def getProfiles(psId: String): Map[String,Set[String]] = {
    getDocument(psId, topDirectory) match {
      case Some(doc) => doc.getFields.asScala.
                                     foldLeft[Map[String,Set[String]]] (Map()) {
        case (map, field) =>
          val id = field.name()
          if (id.equals("id")) map
          else map + ((id, field.stringValue().split(" ").toSet))
      }
      case None => Map()
    }
  }

  def getSimDocsXml(psId: String,
                    profiles: Set[String],
                    outFields: Set[String],
                    maxDocs: Int): String = {
    val head = """<?xml version="1.0" encoding="UTF-8"?><documents>"""

    getSimDocs(psId, profiles, outFields, maxDocs).foldLeft[String] (head) {
      case (str,map) => {
        s"${str}<document>" + map.foldLeft[String]("") {
          case (str2, (tag,lst)) => {
            lst.size match {
              case 0 => str2
              case 1 => str2 + s"<$tag>${cleanString(lst(0))}</$tag>"
              case _ => str2 + s"<${tag}_list>" + lst.foldLeft[String]("") {
                case (str3,elem) => s"$str3<$tag>${cleanString(elem)}</$tag>"
              } + s"</${tag}_list>"
            }
          }
        } + "</document>"
      }
    } + "</documents>"
  }

  def getSimDocs(psId: String,
                 profiles: Set[String],
                 outFlds: Set[String],
                 maxDocs: Int): List[Map[String,List[String]]] = {
    getDocument(psId, topDirectory) match {
      case Some(doc) => {
        val docIds = getDocIds(doc, profiles)
//println(s"docIds=$docIds")
        if (docIds.isEmpty) List()
        else {
          limitDocs(docIds, maxDocs, List()).
                              foldLeft[List[Map[String,List[String]]]](List()) {
            case (lst, id) => {
              val fields = getDocFields(id, sdSearcher, outFlds)
//println(s"fields=$fields")
              if (fields.isEmpty) lst else lst :+ fields
            }
          }
        }
      }
      case None => List()
    }
  }

  private def getDocIds(doc: Document,
                        profiles: Set[String]): List[Set[Int]] = {
    profiles.foldLeft[List[Set[Int]]](List()) {
      case (lst, id) => {
        val did = doc.getFieldable(id)
        if (did == null) lst else {
          val ids = docIndex.getDocIds(did.stringValue())
          if (ids.isEmpty) lst else lst :+ ids
        }
      }
    }
  }

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

  private def getDocFields(id: Int,
                           searcher: IndexSearcher,
                           fields: Set[String]): Map[String,List[String]] = {
    val doc = searcher.doc(id)

    if (fields.isEmpty) { // put all fields
      doc.getFields().asScala.foldLeft[Map[String,List[String]]] (Map()) {
        case  (map, field:Fieldable) => {
          val name = field.name()
          val lst = map.getOrElse(name,List[String]())
          map + ((name, field.stringValue() :: lst))
        }
      }
    } else fields.foldLeft[Map[String,List[String]]] (Map()) {
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

  private def getDocument(id: String,
                          dir: FSDirectory): Option[Document] = {
    val searcher = new IndexSearcher(dir)
    val doc = getDocument(id, searcher)

    searcher.close()
    doc
  }

  private def getDocument(id: String,
                          searcher: IndexSearcher): Option[Document] = {
    val docs = searcher.search(new TermQuery(new Term("id", id)), 1)

    docs.totalHits match {
      case 0 => None
      case _ => Some(searcher.doc(docs.scoreDocs(0).doc))
    }
  }

  private def cleanString(in: String): String = {
    in.replace("\"", "&quot;").replace("&", "&amp;").replace("''", "&pos;").
       replace("<", "&lt;").replace(">", "&gt;")
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
        if (s3.isEmpty) Failure(new IOException("empty title, h1 and h2"))
        else Success(s3)
      case Failure(x) => Failure(x)
    }
  }
}
