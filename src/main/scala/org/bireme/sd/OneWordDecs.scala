/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/


package org.bireme.sd

import bruma.master._
import java.io.File

import org.apache.lucene.analysis.core.KeywordAnalyzer
import org.apache.lucene.document.{Document, Field, StoredField, TextField}
import org.apache.lucene.index.{DirectoryReader, IndexWriter, IndexWriterConfig, Term}
import org.apache.lucene.search.{IndexSearcher, TermQuery, TopDocs}
import org.apache.lucene.store.FSDirectory

import scala.collection.JavaConverters._

object OneWordDecs {
  def createIndex(decsDir: String,
                  indexPath: String): Unit = {
    require(decsDir != null)
    require(indexPath != null)

    val analyzer = new KeywordAnalyzer()
    val directory = FSDirectory.open(new File(indexPath).toPath)
    val config = new IndexWriterConfig(analyzer)
    config.setOpenMode(IndexWriterConfig.OpenMode.CREATE)
    val indexWriter = new IndexWriter(directory, config)
    val mst = MasterFactory.getInstance(decsDir).open()

    mst.iterator().asScala.foreach(rec => createDoc(rec).foreach(indexWriter.addDocument(_)))
    indexWriter.forceMerge(1)
    indexWriter.close()
    directory.close()
  }

  private def createDoc(rec: Record): Option[Document] = {
    if (rec.isActive) {
      // Create a set with synonyms with only one word
      val sims = getFldSyns(rec, 50) ++ getFldSyns(rec, 23)

      if (sims.isEmpty) None
      else {
        val doc = new Document()
        val fld1 = rec.getField(1,1)
        val fld2 = rec.getField(2,1)
        val fld3 = rec.getField(3,1)

        doc.add(new StoredField("id", rec.getMfn))
        if (fld1 != null) doc.add(new TextField("descriptor",
          Tools.uniformString(fld1.getContent.trim()), Field.Store.YES))
        if (fld2 != null) doc.add(new TextField("descriptor",
          Tools.uniformString(fld2.getContent.trim()), Field.Store.YES))
        if (fld3 != null) doc.add(new TextField("descriptor",
          Tools.uniformString(fld3.getContent.trim()), Field.Store.YES))
        sims.foreach(syn => doc.add(new StoredField("synonym",
          Tools.uniformString(syn.trim()))))
        Some(doc)
      }
    } else None
  }

  private def getFldSyns(rec: Record,
                         tag: Int): Set[String] = {
    require (rec != null)
    require (tag > 0)

    val subIds = Set('i', 'e', 'p')

    rec.getFieldList(tag).asScala.foldLeft[Set[String]](Set()) {
      case (set,fld) => fld.getSubfields.asScala.
        filter(sub => subIds.contains(sub.getId)).foldLeft[Set[String]](set) {
          case (s,sub) =>
            val split = sub.getContent.trim().split(" +", 2)
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
      val descr: Seq[String] = inWords.slice(beginPos, endPos + 1)
      val descrStr: String = descr.mkString(" ")
      val query: TermQuery = new TermQuery(new Term("descriptor", descrStr))
      val topDocs: TopDocs = decsSearcher.search(query, 1)

      if (topDocs.totalHits.value > 0) {
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

  if (args.length != 2) usage()

  OneWordDecs.createIndex(args(0), args(1))
}

object OneWordDecsTest extends App {
  private def usage(): Unit = {
    Console.println("usage: OneWordDecsTest <decsIndex> <sentence>")
    System.exit(1)
  }

  if (args.length != 2) usage()

  val decsDirectory = FSDirectory.open(new File(args(0)).toPath)
  val decsReader = DirectoryReader.open(decsDirectory)
  val decsSearcher = new IndexSearcher(decsReader)
  val outSentence = OneWordDecs.addDecsSynonyms(args(1), decsSearcher)

  decsReader.close()
  decsDirectory.close()

  System.out.println("in:" + args(1))
  System.out.println("out:" + outSentence)
}
