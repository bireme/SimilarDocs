package org.bireme.sd

import java.io.File

object XMLParserTest extends App {
  private def usage(): Unit = {
    Console.err.println("usage: XMLParserTest (-file=<xmlFile>|-dir=<xmlDir>) " +
      "[-fields=<field1>,...,<fieldN>] [-encoding=<str>]")
    System.exit(1)
  }

  if (args.length < 1) usage()

  val parameters = args.foldLeft[Map[String,String]](Map()) {
    case (map,par) =>
      val split = par.split(" *= *", 2)
      map + ((split(0).substring(1), split(1)))
  }

  val files = if (parameters.contains("file")) Set(parameters("file")) else {
    new File(parameters("dir")).listFiles().foldLeft[Set[String]] (Set()) {
      case (set,file) =>
        val xmlFile = file.getPath
        if (xmlFile.toLowerCase().endsWith(".xml")) set + xmlFile
        else set
    }
  }

  val sFields = parameters.getOrElse("fields", "")

  val fldNames = if (sFields.isEmpty) Set[String]()
                 else sFields.split(" *, *").toSet

  val encoding = parameters.getOrElse("encoding", "utf-8")

  files.foreach {
    file =>
      println(s"==file: $file==")
      IahxXmlParser.getElements(file, encoding, fldNames).foreach {
        map => {
          map.foreach { case (k,v) => println(s"\t$k => $v") }
          println()
        }
      }
  }
}
