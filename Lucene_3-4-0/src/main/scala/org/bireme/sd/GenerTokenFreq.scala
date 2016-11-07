package org.bireme.sd

import java.io.File

import java.text.Normalizer
import java.text.Normalizer.Form

import org.apache.lucene.index.{IndexReader, Term, TermDocs}
import org.apache.lucene.store.FSDirectory

import scala.collection.immutable.TreeMap

class GenerTokenFreq(reader: IndexReader) {
  private val separators =
              """[\d\s\,\.\-;\"\=\<\>\$\&\:\+\%\*\@\?\!\~\^\(\)\[\]`'/¿#_\\]+"""


  def process(str: String,
              fields: Set[String]): Map[Int,String] = {
    process(uniformString(str).split(separators).toSet, fields)
  }

  def process(words: Set[String],
              fields: Set[String]): Map[Int,String] = {
    val map = words.filter(_.length() > 2).foldLeft [Map[String,Int]] (Map()) {
      case (map, tok) =>
        val total = fields.foldLeft[Set[Int]] (Set()) {
          case (set, fld) => totalTermDocs(tok, fld, set, reader)
        }
        map + ((tok, total.size))
    }
    order(map)
  }

  private def totalTermDocs(key: String,
                            field: String,
                            wSet: Set[Int],
                            reader: IndexReader): Set[Int] = {
    val termDocs = reader.termDocs(new Term(field, key))
    val total = totalTermDocs(termDocs, wSet)

    termDocs.close()
    total
  }

  private def totalTermDocs(termDocs: TermDocs,
                            wSet: Set[Int]): Set[Int] = {
    if (termDocs.next) totalTermDocs(termDocs, wSet + termDocs.doc())
    else wSet
  }

  private def uniformString(in: String): String = {
    val s = Normalizer.normalize(in.trim().toLowerCase(), Form.NFD)
    s.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
  }

  private def order(map: Map[String,Int]): Map[Int,String] = {
    map.foldLeft[Map[Int,String]] (TreeMap()) {
      case (tm, (k,v)) => tm + ((v,k))
    }
  }
}

object GenerTokenFreq extends App {
  private def usage(): Unit = {
    Console.err.println(
      "usage: GenerTokenFreq <inIndex> <field>,<field>,...,<field> <inString>")
    System.exit(1)
  }

  if (args.length != 3) usage()

  val fields = args(1).split("\\,").map(_.trim).toSet
  val inDir = FSDirectory.open(new File(args(0)))
  val reader = IndexReader.open(inDir)
  val map = new GenerTokenFreq(reader).process(args(2), fields)

  map.toSeq.zipWithIndex.foreach {
    case ((tot, tok),idx) => println(s"$idx.[$tok] => $tot")
  }
  reader.close()
  inDir.close()
}
