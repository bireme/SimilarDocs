/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/


package org.bireme.sd

import bruma.master._

import java.io.File
import java.nio.file.Path
import org.apache.lucene.analysis.core.KeywordAnalyzer
import org.apache.lucene.document.{Document, Field, StoredField, TextField}
import org.apache.lucene.index.{DirectoryReader, IndexWriter, IndexWriterConfig, Term}
import org.apache.lucene.search.{IndexSearcher, TermQuery, TopDocs}
import org.apache.lucene.store.FSDirectory
import org.bireme.dh.{Config, Highlighter}

import scala.jdk.CollectionConverters._
import scala.collection.mutable

/**
  * Create a Lucene index with documents having DeCS descriptors and synonyms
  */
object OneWordDecs {
  private val conf: Config = Config(None, None, scanMainHeadings=true, scanEntryTerms=true, scanQualifiers=true,
    scanPublicationTypes=true, scanCheckTags=true, scanGeographics=true)

  /**
    * Create a Lucene index with documents having DeCS descriptors and synonyms
    * @param decsDir Isis database path having DeCS records
    * @param indexPath destination Lucene index with DeCS descritors/synonyms
    */
  def createIndex(decsDir: String,
                  indexPath: String): Unit = {
    require(decsDir != null)
    require(indexPath != null)

    val analyzer: KeywordAnalyzer = new KeywordAnalyzer()
    val directory: FSDirectory = FSDirectory.open(new File(indexPath).toPath)
    val config: IndexWriterConfig = new IndexWriterConfig(analyzer)
    config.setOpenMode(IndexWriterConfig.OpenMode.CREATE)
    val indexWriter: IndexWriter = new IndexWriter(directory, config)
    //val mst: Master = MasterFactory.getInstance(decsDir).setEncoding("IBM850").open()
    val mst: Master = MasterFactory.getInstance(decsDir).setEncoding("ISO8859-1").open()

    mst.iterator().asScala.foreach {
      rec => createDoc(rec).foreach {
        fld => indexWriter.addDocument(fld)
      }
    }
    indexWriter.forceMerge(1)
    indexWriter.close()
    directory.close()
  }

  /**
    * Given an Isis DeCS record, create a Lucene document with descriptors and synonyms
    * @param rec input DeCs record
    * @return a Lucene document with descriptors and synonyms
    */
  private def createDoc(rec: Record): Option[Document] = {
    if (rec.isActive) {
      // Create a set with synonyms with only one word
      val sims: Set[String] = getFldSyns(rec, 50) ++ getFldSyns(rec, 23)

      if (sims.isEmpty) None
      else {
        val doc = new Document()
        val fld1 = rec.getField(1,1)
        val fld2 = rec.getField(2,1)
        val fld3 = rec.getField(3,1)

        doc.add(new StoredField("id", rec.getMfn))
        if (fld1 != null) doc.add(new TextField("descriptor",
          org.bireme.dh.Tools.uniformString2(fld1.getContent.trim())._1, Field.Store.YES))
        if (fld2 != null) doc.add(new TextField("descriptor",
          org.bireme.dh.Tools.uniformString2(fld2.getContent.trim())._1, Field.Store.YES))
        if (fld3 != null) doc.add(new TextField("descriptor",
          org.bireme.dh.Tools.uniformString2(fld3.getContent.trim())._1, Field.Store.YES))
        sims.foreach(syn => doc.add(new StoredField("synonym",
          org.bireme.dh.Tools.uniformString2(syn.trim())._1)))
        Some(doc)
      }
    } else None
  }

  /**
    *
    * @param rec input Isis record
    * @param tag record tag used to retrieve synonyms
    * @return a set of DeCS synonyms (those with only one word) of the input record
    */
  private def getFldSyns(rec: Record,
                         tag: Int): Set[String] = {
    require (rec != null)
    require (tag > 0)

    val subIds = Set('i', 'e', 'p')

    rec.getFieldList(tag).asScala.foldLeft[Set[String]](Set()) {
      case (set,fld) => fld.getSubfields.asScala.
        filter(sub => subIds.contains(sub.getId)).foldLeft[Set[String]](set) {
          case (s,sub) =>
            val content: String = sub.getContent.trim()
            if (content.contains(" ")) s + content else s
        }
    }
  }

  /**
    * Given an input text, for each DeCS descriptor found, add its synonyms
    * @param sentence input text
    * @param decsSearcher lucene DeCS index
    * @param highlighter the object which will locate DeCS terms in a sentence
    * @return the input text with DeCS synonyms added
    */
  def addDecsSynonyms(sentence: String,
                      decsSearcher: IndexSearcher,
                      highlighter: Highlighter): String = {
    require (sentence != null)
    require (decsSearcher != null)
    require (highlighter != null)

    val synonyms: Set[String] = getDecsSynonyms(sentence.trim(), decsSearcher, highlighter, conf)

    sentence + " " + synonyms.mkString(" ")
  }

  /**
    * Given an input text, for each DeCS descriptor found, add its synonyms
    * @param inText input text
    * @param decsSearcher lucene DeCS index
    * @param highlighter the object which will locate DeCS terms in a sentence
    * @param conf configuration indicating which type of DeCS terms should be highlighted
    * @return the input text with DeCS synonyms added
    */
  private def getDecsSynonyms(inText: String,
                              decsSearcher: IndexSearcher,
                              highlighter: Highlighter,
                              conf: Config): Set[String] = {

    val (_,_, descripts: Seq[String]) = highlighter.highlight("", "", inText, conf)

    descripts.foldLeft(mutable.Set[String]()) {
      case (set, descr) =>
        val query: TermQuery = new TermQuery(new Term("descriptor", descr))
        val topDocs: TopDocs = decsSearcher.search(query, 1)

         if (topDocs.totalHits.value > 0) {   // Lucene 8.0.0
        //if (topDocs.totalHits > 0) {
          val doc = decsSearcher.storedFields().document(topDocs.scoreDocs(0).doc) // decsSearcher.doc(topDocs.scoreDocs(0).doc)
          doc.getValues("synonym").foldLeft(set) {
            case (set, fld) => set += fld
          }
        } else set
    }.toSet
  }
}

object OneWordDecsCreate extends App {
  private def usage(): Unit = {
    Console.println("usage: OneWordDecsCreate <decsDir> <decsIndex>")
    Console.println("\t<decsDir> - Isis database path having DeCS records")
    Console.println("\t<decsIndex> - Lucene index with DeCS descritors/synonyms")
    System.exit(1)
  }

  if (args.length != 2) usage()

  OneWordDecs.createIndex(args(0), args(1))
}

object OneWordDecsTest extends App {
  private def usage(): Unit = {
    Console.println("usage: OneWordDecsTest <decsIndex> <sentence>")
    Console.println("\t<decsIndex> - Lucene index with DeCS descritors/synonyms")
    Console.println("\t<sentence> - text from which the descritors will be looked for")
    System.exit(1)
  }

  if (args.length != 2) usage()

  private val decsPath: Path = new File(args(0)).toPath
  private val decsDirectory = FSDirectory.open(decsPath)
  private val decsReader = DirectoryReader.open(decsDirectory)
  private val decsSearcher = new IndexSearcher(decsReader)
  private val highlighter = new Highlighter(args(0))
  private val outSentence = OneWordDecs.addDecsSynonyms(args(1), decsSearcher, highlighter)

  decsReader.close()
  decsDirectory.close()
  highlighter.close()

  System.out.println("in:" + args(1))
  System.out.println("out:" + outSentence)
}
