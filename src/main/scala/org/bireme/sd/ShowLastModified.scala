/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.sd

import java.util

import org.h2.mvstore.{MVMap, MVStore}

//import scala.collection.JavaConverters._
import scala.jdk.CollectionConverters._

/**
* Show the records of the MapDB database / table modFile
  */
object ShowLastModified extends App {
  private def usage(): Unit = {
    System.err.println("usage ShowLastModified <dbPath> <table>")
    System.exit(1)
  }

  if (args.length != 2) usage()

  private val fileLastModified: MVStore = new MVStore.Builder().fileName(args(0)).compress().readOnly().open()
  private val lastModifiedFile: MVMap[String, Long] = fileLastModified.openMap(args(1))

  lastModifiedFile.entrySet().asScala.foreach {
    entry: util.Map.Entry[String, Long] => println(s"key[${entry.getKey}]=${entry.getValue}")
  }
}
