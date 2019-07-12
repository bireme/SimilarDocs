/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/


package org.bireme.sd.service

import org.bireme.sd.SimDocsSearch

/** Application to regularly update all similar documents into lucene indexes
*
* author: Heitor Barbieri
* date: 20170110
*/
object UpdaterBatchService extends App {
  private def usage(): Unit = {
    Console.err.println("usage: UpdateBatchService:\n" +
      "\n\t-sdIndexPath=<path>     : documents Lucene index path" +
      "\n\t-topIndexPath=<path>    : top indexes directory path" +
      "\n\t-decsIndexPath=<path>   : decs indexes directory path"
    )
    System.exit(1)
  }
  if (args.length != 3) usage()

  val parameters = args.foldLeft[Map[String,String]](Map()) {
    case (map,par) =>
      val split = par.split(" *= *", 2)
      if (split.length == 2) map + ((split(0).substring(1), split(1)))
      else map + ((split(0).substring(2), ""))
  }
  val topIndexPath = parameters("topIndexPath")
  val sdIndexPath = parameters("sdIndexPath")
  val decsIndexPath = parameters("decsIndexPath")
  val sdSearcher = new SimDocsSearch(sdIndexPath, decsIndexPath)

  update(sdSearcher, topIndexPath)
  sdSearcher.close()

  def update(sdSearcher: SimDocsSearch,
             topIndexPath: String): Unit = {
    val topIndex = new TopIndex(sdSearcher, topIndexPath)

    topIndex.updateSimilarDocs()

    // Only to create the top index if it does not exist.
    topIndex.close()
  }
}
