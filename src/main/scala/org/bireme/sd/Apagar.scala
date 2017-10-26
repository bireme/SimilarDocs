package org.bireme.sd

import java.io.File

import org.apache.lucene.document.{Document,Field,TextField}
import org.apache.lucene.index.{IndexWriter,IndexWriterConfig}
import org.apache.lucene.store.FSDirectory
//import org.apache.lucene.analysis.core.WhitespaceAnalyzer

import scala.io._

object Apagar extends App {
  val filePath = "/home/heitor/sbt-projects/SimilarDocs/wprim-224874.txt"
  val indexPath = "apagarIndex"
  val analyzer0 = new NGramAnalyzer(NGSize.ngram_min_size,
                                   NGSize.ngram_max_size)
  //val analyzer = new WhitespaceAnalyzer()
  val indexPath2 = new File(indexPath.trim).toPath()
  val directory = FSDirectory.open(indexPath2)
  val config = new IndexWriterConfig(analyzer0)
  config.setOpenMode(IndexWriterConfig.OpenMode.CREATE)
  val indexWriter = new IndexWriter(directory, config)

  val src = Source.fromFile(filePath, "iso-8859-1")
  val str0 = src.getLines.mkString(" ")
  println(s"len=${str0.size}")
  val str = str0.substring(0, 12000)
  val str1 = "Ãbcdef gHijkLmnÓp qrstüvwxyz01234567890"
  val doc = new Document()

  println(s"str=[$str]")
  doc.add(new TextField("ab", str+str1, Field.Store.YES))
  indexWriter.addDocument(doc)
  //str.foreach(ch => println(s"$ch[${ch.toInt}]"))
  src.close()
  indexWriter.close()

  println("indexação is OK")
}
