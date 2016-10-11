package org.bireme.sd

import java.io.File

import org.apache.lucene.document.{Document,Field}
import org.apache.lucene.index.{IndexWriter,IndexWriterConfig}
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.Version

import scala.io.Source

object IndexDocs extends App {

  private def usage(): Unit = {
    Console.err.println(
      "usage: IndexDocs -inFile=<name> -index=<path>" +
      "\n\t\t[-idxFldName=<name>]" +
      "\n\t\t[-validTokenChars=<ch><ch>...<ch>]" +
      "\n\t\t[-encoding=<str>]" +
      "\n\t\t[--doNotTokenize] [--uniformToken]")
    System.exit(1)
  }

  if (args.length < 2) usage()

  val parameters = args.foldLeft[Map[String,String]](Map()) {
    case (map,par) => {
      val split = par.split(" *= *", 2)
      if (split.length == 2) map + ((split(0).substring(1), split(1)))
      else map + ((split(0).substring(2), ""))
    }
  }

  val inFile = parameters("inFile")
  val index = parameters("index")
  val idxFldName = parameters.getOrElse("idxFldName", "idxField")
  val valTokChars = parameters.getOrElse("validTokenChars", "")
  val validTokenChars = if (valTokChars.isEmpty()) SDTokenizer.defValidTokenChars
                        else valTokChars.toSet
  val encoding = parameters.getOrElse("encoding", "utf-8")
  val tokenize = !parameters.contains("doNotTokenize")
  val uniform = parameters.contains("uniformToken")

  create()

  def create(): Unit = {
    val analyzer = new SDAnalyzer(validTokenChars, uniform)
    val directory = FSDirectory.open(new File(index))
    val config = new IndexWriterConfig(Version.LUCENE_34, analyzer)
    val iwriter = new IndexWriter(directory, config)
    val src = Source.fromFile(inFile, encoding)

    src.getLines().zipWithIndex.foreach {
      case (line,pos) => {
        if (pos % 10000 == 0) println(s"+++$pos")
        val split = line.trim.split(""" *\| *""", 3)
        if (split.length == 3) {
          val doc = new Document()
          doc.add(new Field("dbname", split(0), Field.Store.YES, Field.Index.NOT_ANALYZED))
          doc.add(new Field("id", split(1), Field.Store.YES, Field.Index.NOT_ANALYZED))
          doc.add(new Field(idxFldName, split(2), Field.Store.YES, Field.Index.ANALYZED))
          iwriter.addDocument(doc)
        }
      }
    }
    print("Optimizing index ...")
    iwriter.optimize()
    iwriter.close()
    println("Ok")
  }
}
