/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.sd

import org.mapdb.{DB, DBMaker, HTreeMap, Serializer}

object IdSearch extends App {
  private def usage(): Unit = {
    Console.err.println("usage: IdSearch <mapDBFilePath> <id>")
    System.exit(1)
  }
  if (args.length != 2) usage()

  val db: DB = DBMaker.fileDB(args(0)).closeOnJvmShutdown().make
  val allDocIds: HTreeMap.KeySet[Integer] = db.hashSet("idSet", Serializer.INTEGER).createOrOpen()

  println("Contains: "+ allDocIds.contains(args(1).hashCode))

  db.close()
}
