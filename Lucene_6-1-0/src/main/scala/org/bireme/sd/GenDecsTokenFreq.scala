package org.bireme.sd

import java.io.BufferedWriter
import java.nio.charset.Charset
import java.nio.file.{Files,Paths}
import java.text.Normalizer
import java.text.Normalizer.Form

import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.document.{Document,Field,StoredField,StringField,TextField}
import org.apache.lucene.index.{IndexWriter,IndexWriterConfig}
import org.apache.lucene.store.FSDirectory

import scala.collection.immutable.TreeMap
import scala.io.Source

object GenDecsTokenFreq extends App {
  private def usage(): Unit = {
    Console.err.println("usage: GenDecsTokenFreq <decsInFile> <lilInFile> <inEncoding> <indexPath>")
    System.exit(1)
  }

  if (args.length != 4) usage()

  val separators = """[\d\s\,\.\-;\"\=\<\>\$\&\:\+\%\*\@\?\!\~\^\(\)\[\]`'/¿#_]"""

  gener(args(0), args(1), args(2), args(3))

  def gener(decsInFile: String,
            lilInFile: String,
            inEncoding: String,
            index: String): Unit = {
    val in = Source.fromFile(decsInFile, inEncoding)
    val analyzer = new WhitespaceAnalyzer()
    val config = new IndexWriterConfig(analyzer)
    val directory = FSDirectory.open(Paths.get(index))
    val iwriter = new IndexWriter(directory, config)
    val freq = genDbFreq(lilInFile, inEncoding)
    val set = in.getLines().zipWithIndex.
                                     foldLeft[Set[(String,String,Int)]](Set()) {
      case (st, (line,pos)) => {
        if (pos % 10000 == 0) println(s"decs-$pos")
        val split = line.split("\\|", 3)
        uniformString(split(2)).split(separators).
                                        foldLeft[Set[(String,String,Int)]](st) {
          case (st2, word) => {
            val word2 = word.trim
            if (word2.isEmpty) st2 else {
              val qtt = freq.getOrElse(word2, 0)
              st2 + ((split(1),word2,qtt))
            }
          }
        }
      }
    }
    set.foreach {
      case (id,word,qtt) => {
        val doc = new Document()
        doc.add(new StringField("dbname", "decs", Field.Store.YES))
        doc.add(new StringField("id", id, Field.Store.YES))
        doc.add(new StoredField("freq", qtt))
        doc.add(new TextField("token", word, Field.Store.YES))
        iwriter.addDocument(doc);
      }
    }

    print("Optimizing index ...")
    iwriter.forceMerge(1)
    iwriter.close()
    println("Ok")

    in.close()
  }

  def genDbFreq(inFile: String,
                inEncoding: String): Map[String,Int] = {
    val in = Source.fromFile(inFile, inEncoding)
    val map = in.getLines().zipWithIndex.foldLeft[Map[String,Int]](TreeMap()) {
      case (mp, (line,pos)) => {
        if (pos % 10000 == 0) println(s"db-$pos")
        val split = line.split("\\|", 3)
        if (split.size == 3) {
          uniformString(split(2)).split(separators).
                                                   foldLeft[Map[String,Int]](mp) {
            case (mp2, word) => {
              val word2 = word.trim
              if (word2.isEmpty) mp2 else {
                val qtt = mp2.getOrElse(word2, 0)
                mp2 + ((word2, qtt + 1))
              }
            }
          }
        } else mp
      }
    }
    in.close()
    map
  }

  private def uniformString(in: String): String = {
    val s = Normalizer.normalize(in.toLowerCase(), Form.NFD)
    s.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
  }
}