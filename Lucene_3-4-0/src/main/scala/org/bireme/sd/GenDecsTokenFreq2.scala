package org.bireme.sd

import java.text.Normalizer
import java.text.Normalizer.Form
import java.io.{BufferedWriter,File}
import java.nio.charset.Charset
import java.nio.file.Files

import collection.JavaConverters._

import org.apache.lucene.analysis.WhitespaceAnalyzer
import org.apache.lucene.document.{Document,Field,NumericField}
import org.apache.lucene.index.{IndexReader,IndexWriter,IndexWriterConfig}
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.Version

import scala.collection.immutable.TreeMap
import scala.io.Source

object GenDecsTokenFreq2 extends App {
  val separators = """[\d\s\,\.\-;\"\=\<\>\$\&\:\+\%\*\@\?\!\~\^\(\)\[\]`'/¿#_]"""

  private def usage(): Unit = {
    Console.err.println("usage: GenDecsTokenFreq2 <decsInFile> <decsEncoding> " +
                        "<inIndexPath> <field1>,<fields2>,..,<fieldN> " +
                        "<freqIndexPath>")
    System.exit(1)
  }

  if (args.length != 5) usage()

  gener(args(0), args(1), args(2), args(3).split("\\,").toSet, args(4))


  def gener(decsInFile: String,
            decsEncoding: String,
            inIndex: String,
            fldNames: Set[String],
            outIndex: String): Unit = {
    val in = Source.fromFile(decsInFile, decsEncoding)
    val analyzer = new WhitespaceAnalyzer(Version.LUCENE_34)
    val config = new IndexWriterConfig(Version.LUCENE_34, analyzer)
    val inDir = FSDirectory.open(new File(inIndex))
    val inReader = IndexReader.open(inDir);
    val outDir = FSDirectory.open(new File(outIndex))
    val outWriter = new IndexWriter(outDir, config)    
    val freq = genDbFreq(inReader, fldNames)
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
        doc.add(new Field("dbname", "decs", Field.Store.YES, Field.Index.NOT_ANALYZED))
        doc.add(new Field("id", id, Field.Store.YES, Field.Index.NOT_ANALYZED))
        doc.add(new NumericField("freq", Field.Store.YES, false).setIntValue(qtt))
        doc.add(new Field("token", word, Field.Store.YES,  Field.Index.ANALYZED))
        outWriter.addDocument(doc);
      }
    }

    print("Optimizing index ...")
    outWriter.optimize()
    outWriter.close()
    outDir.close()
    println("Ok")

    in.close()
    inReader.close()
    inDir.close()
  }


  private def genDbFreq(reader: IndexReader,
                         fldNames: Set[String]): Map[String,Int] = {
    //val names = scala.collection.mutable.Set(fldNames.toArray:_*)
    val last = reader.maxDoc() - 1
    (0 to last).foldLeft[Map[String,Int]](TreeMap()) {
      case(map,docID) => {
        if (docID % 10000 == 0) println(s"db-$docID")
        val doc = reader.document(docID)
        genDbFreq(doc, fldNames, map)
      }
    }
  }

  private def genDbFreq(doc: Document,
                         fldNames: Set[String],
                         imap: Map[String,Int]): Map[String,Int] = {
    fldNames.foldLeft[Map[String,Int]](imap) {
      case(map,fname) => {
        val fld = doc.get(fname)
        if (fld == null) map else {
          uniformString(fld).split(separators).foldLeft[Map[String,Int]](map) {
            case (mp2, word) => {
              val word2 = word.trim
              if (word2.isEmpty) mp2 else {
                val qtt = mp2.getOrElse(word2, 0)
                mp2 + ((word2, qtt + 1))
              }
            }
          }
        }
      }
    }
  }

  private def uniformString(in: String): String = {
    val s = Normalizer.normalize(in.toLowerCase(), Form.NFD)
    s.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
  }

}
