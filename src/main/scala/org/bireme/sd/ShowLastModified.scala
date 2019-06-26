/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.sd

import java.util

import org.mapdb.{DB, DBMaker, HTreeMap, Serializer}

import scala.collection.JavaConverters._

/**
* Show the records of the MapDB database / table modFile
  */
object ShowLastModified extends App {
  private def usage(): Unit = {
    System.err.println("usage ShowLastModified <dbPath>")
    System.exit(1)
  }

  if (args.length != 1) usage()

  val db: DB = DBMaker.fileDB(args(0)).closeOnJvmShutdown().make
  val lastModified: HTreeMap[String, java.lang.Long] = db.hashMap("modFile", Serializer.STRING, Serializer.LONG).open()

  lastModified.entrySet().asScala.foreach {
    entry: util.Map.Entry[String, java.lang.Long] => println(s"key[${entry.getKey}]=${entry.getValue}")
  }
}
