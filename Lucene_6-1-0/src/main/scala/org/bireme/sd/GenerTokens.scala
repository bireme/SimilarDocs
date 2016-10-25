package org.bireme.sd

import java.io.BufferedWriter
import java.nio.charset.Charset
import java.nio.file.{Files,Paths}
import java.text.Normalizer
import java.text.Normalizer.Form

import scala.collection.immutable.TreeSet
import scala.io.Source

object GenerTokens extends App {
  private def usage(): Unit = {
    Console.err.println("usage: GenerTokens <inFile> <inEncoding> <outFile>")
    System.exit(1)
  }

  if (args.length != 3) usage()

  val separators = """[\d\s\,\.\-\&\:\+\%\(\)'/]"""

  gener(args(0), args(1), args(2))

  def gener(inFile: String,
            inEncoding: String,
            outFile: String): Unit = {
    val in = Source.fromFile(inFile, inEncoding)
    val out = Files.newBufferedWriter(Paths.get(outFile),
                                      Charset.forName("utf-8"))
    val set = in.getLines().foldLeft[Set[String]](TreeSet()) {
      case (set, line) => uniformString(line).split(separators).
                                                    foldLeft[Set[String]](set) {
        case (set, word) => if (word.length >= 3) set + word else set
      }
    }
    set.toList.zipWithIndex.foreach {
      case (word,idx) => out.write(s"decs|${idx+1}|$word\n")
    }

    out.close()
    in.close()
  }

  private def uniformString(in: String): String = {
    val s = Normalizer.normalize(in.toLowerCase(), Form.NFD)
    s.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
  }
}