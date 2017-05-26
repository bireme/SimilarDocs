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

import java.nio.file.Paths
import java.util.Calendar

import org.bireme.sd.SimDocsSearch

import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.FSDirectory

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
      "\n\t-docIndexPath=<path>    : doc indexes directory path" +
      "\n\t-updAllDay=<day-number> : day to update all similar documents " +
                                       "index 0-today 1-sunday 7-saturday"
    )
    System.exit(1)
  }
  if (args.length != 4) usage()

  val parameters = args.foldLeft[Map[String,String]](Map()) {
    case (map,par) => {
      val split = par.split(" *= *", 2)
      if (split.length == 2) map + ((split(0).substring(1), split(1)))
      else map + ((split(0).substring(2), ""))
    }
  }
  val topIndexPath = parameters("topIndexPath")
  val docIndexPath = parameters("docIndexPath")

  val sdIndexPath = parameters("sdIndexPath")
  val sdSearcher = new SimDocsSearch(sdIndexPath)

  val updAllDay = parameters("updAllDay").toInt
  val updateAll = ((updAllDay == 0) ||
                  (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == updAllDay))
  val docIndex = new DocsIndex(docIndexPath, sdSearcher, Conf.minSim, Conf.maxDocs)

  update(sdSearcher, docIndex, topIndexPath, updateAll)
  docIndex.close()
  sdSearcher.close()

  def update(sdSearcher: SimDocsSearch,
             docIndex: DocsIndex,
             topIndexPath: String,
             updateAll: Boolean): Unit = {
    if (updateAll) docIndex.updateAllRecordDocs(Conf.idxFldNames)
    else docIndex.updateNewRecordDocs(Conf.idxFldNames)

    // Only to create the top index if it does not exist.
    new TopIndex(sdSearcher, docIndex, topIndexPath, Conf.idxFldNames).close()
  }
}
