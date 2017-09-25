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

package org.bireme.sd

import bruma.master._

import java.io.File

import org.apache.lucene.analysis.core.KeywordAnalyzer
import org.apache.lucene.document.{Document,Field,StoredField,TextField}
import org.apache.lucene.index.{DirectoryReader, IndexWriter,IndexWriterConfig, Term}
import org.apache.lucene.search.{IndexSearcher,TermQuery}
import org.apache.lucene.store.FSDirectory

import scala.collection.JavaConverters._

object OneWordDecs {
  def createIndex(decsDir: String,
                  indexPath: String): Unit = {
    require(decsDir != null)
    require(indexPath != null)

    val analyzer = new KeywordAnalyzer()
    val directory = FSDirectory.open(new File(indexPath).toPath())
    val config = new IndexWriterConfig(analyzer)
    config.setOpenMode(IndexWriterConfig.OpenMode.CREATE)
    val indexWriter = new IndexWriter(directory, config)
    val mst = MasterFactory.getInstance(decsDir).open()

    mst.iterator().asScala.foreach {
      createDoc(_).foreach(doc => indexWriter.addDocument(doc))
    }
    indexWriter.forceMerge(1)
    indexWriter.close()
    directory.close()
  }

  private def createDoc(rec: Record): Seq[Document] = {
    if (rec.isActive()) {
      // Create a set with synonyms with only one word
      val sims = getFldSyns(rec, 50) ++ getFldSyns(rec, 23)

      if (sims.isEmpty) Seq()
      else (1 to 3).foldLeft[Seq[Document]](Seq()) {
        case (seq,tag) =>
          val fld = rec.getField(tag,1)
          if (fld == null) seq
          else {
            val doc = new Document()
            doc.add(new TextField("descriptor",
              Tools.uniformString(fld.getContent.trim()), Field.Store.YES))
            doc.add(new StoredField("id", rec.getMfn()))
            sims.foreach(syn => doc.add(new StoredField("synonym",
                                              Tools.uniformString(syn.trim()))))
            seq :+ doc
          }
      }
    } else Seq()
  }

  private def getFldSyns(rec: Record,
                         tag: Int): Set[String] = {
    require (rec != null)
    require (tag > 0)

    val subIds = Set('i', 'e', 'p')

    rec.getFieldList(tag).asScala.foldLeft[Set[String]](Set()) {
      case (set,fld) => fld.getSubfields().asScala.
        filter(sub => subIds.contains(sub.getId())).foldLeft[Set[String]](set) {
          case (s,sub) =>
            val split = sub.getContent().trim().split(" +", 2)
            if (split.size == 1) s + split.head else s
        }
    }
  }

  def addDecsSynonyms(sentence: String,
                      decsSearcher: IndexSearcher): String = {
    require (sentence != null)
    require (decsSearcher != null)

    val inWords = Tools.uniformString(sentence.trim()).split(" +").toSeq

    getDecsSynonyms(inWords, 0, inWords.size - 1, decsSearcher).mkString(" ")
  }

  private def getDecsSynonyms(inWords: Seq[String],
                              beginPos: Int,
                              endPos: Int,
                              decsSearcher: IndexSearcher): Set[String] = {
    require (inWords != null)
    require (beginPos >= 0)
    require (beginPos <= endPos)
    require (decsSearcher != null)

    if (inWords.isEmpty) Set()
    else if (endPos >= inWords.size) inWords.toSet
    else {
      val descr = inWords.slice(beginPos, endPos + 1)
      val descrStr = descr.mkString(" ")
      val query = new TermQuery(new Term("descriptor", descrStr))
      val topDocs = decsSearcher.search(query, 1)

      if (topDocs.totalHits > 0) {
        val doc = decsSearcher.doc(topDocs.scoreDocs(0).doc)
        doc.getFields("synonym").foldLeft[Set[String]](Set()) {
          case (set,fld) => set + fld.stringValue()
        } ++
        (if (endPos + 1 <= inWords.size - 1)
          descr.toSet ++ getDecsSynonyms(inWords, endPos + 1, inWords.size - 1, decsSearcher)
         else descr.toSet)
      } else {
        if (endPos > beginPos)
          getDecsSynonyms(inWords, beginPos, endPos - 1, decsSearcher)
        else {
          if (endPos + 1 <= inWords.size - 1) {
            Set(inWords(beginPos)) ++
              getDecsSynonyms(inWords, endPos + 1, inWords.size - 1, decsSearcher)
          } else Set(inWords(beginPos))
        }
      }
    }
  }
}

object OneWordDecsCreate extends App {
  private def usage(): Unit = {
    Console.println("usage: OneWordDecsCreate <decsDir> <decsIndex>")
    System.exit(1)
  }

  if (args.size != 2) usage()

  OneWordDecs.createIndex(args(0), args(1))
}

object OneWordDecsTest extends App {
  private def usage(): Unit = {
    Console.println("usage: OneWordDecsTest <decsIndex> <sentence>")
    System.exit(1)
  }

  if (args.size != 2) usage()

  val decsDirectory = FSDirectory.open(new File(args(0)).toPath())
  val decsReader = DirectoryReader.open(decsDirectory)
  val decsSearcher = new IndexSearcher(decsReader)
  val outSentence = OneWordDecs.addDecsSynonyms(args(1), decsSearcher)

  decsReader.close()
  decsDirectory.close()

  System.out.println("in:" + args(1))
  System.out.println("out:" + outSentence)
}