/*=========================================================================

    Copyright © 2017 BIREME/PAHO/WHO

    This file is part of SimilarDocs.

    SimilarDocs is free software: you can redistribute it and/or
    modify it under the terms of the GNU Lesser General Public License as
    published by the Free Software Foundation, either version 2.1 of
    the License, or (at your option) any later version.

    SimilarDocs is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public
    License along with SimilarDocs. If not, see <http://www.gnu.org/licenses/>.

=========================================================================*/

package org.bireme.sd.service

import org.bireme.sd.SimDocsSearch

/** Application to regularly update all similar documents into lucene indexes
*
* @author: Heitor Barbieri
* date: 20170110
*/
object UpdaterBatchService extends App {
  private def usage(): Unit = {
    Console.err.println("usage: UpdateBatchService:\n" +
      "\n\t-sdIndexPath=<path>     : documents Lucene index path" +
      "\n\t-topIndexPath=<path>    : top indexes directory path" +
      "\n\t-decsIndexPath=<path>    : decs indexes directory path"
    )
    System.exit(1)
  }
  if (args.length != 3) usage()

  val parameters = args.foldLeft[Map[String,String]](Map()) {
    case (map,par) => {
      val split = par.split(" *= *", 2)
      if (split.length == 2) map + ((split(0).substring(1), split(1)))
      else map + ((split(0).substring(2), ""))
    }
  }
  val topIndexPath = parameters("topIndexPath")
  val sdIndexPath = parameters("sdIndexPath")
  val decsIndexPath = parameters("decsIndexPath")
  val sdSearcher = new SimDocsSearch(sdIndexPath, decsIndexPath)

  update(sdSearcher, topIndexPath)
  sdSearcher.close()

  def update(sdSearcher: SimDocsSearch,
             topIndexPath: String): Unit = {
    val topIndex = new TopIndex(sdSearcher, topIndexPath, Conf.idxFldNames)

    topIndex.updateSimilarDocs()

    // Only to create the top index if it does not exist.
    topIndex.close()
  }
}
