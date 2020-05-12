/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.sd

import org.h2.mvstore.{MVMap, MVStore}


object IdSearch extends App {
  private def usage(): Unit = {
    Console.err.println("usage: IdSearch <mapDBFilePath> <tableName> <id>")
    System.exit(1)
  }
  if (args.length != 3) usage()

  val store: MVStore = MVStore.open(args(0))
  val allDocIds: MVMap[String, Long] = store.openMap(args(1))

  println("Contains: " + Option(allDocIds.get(args(2))).isEmpty)

  store.close()
}
