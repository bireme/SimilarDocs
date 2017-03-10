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
      "\n\t-docIndexPath=<path>    : doc indexes directory path" +
      "\n\t-updAllDay=<day-number> : day to update all similar documents " +
                                       "index 0-today 1-sunday 7-saturday"
    )
    System.exit(1)
  }

  if (args.length != 3) usage()

  val minSim = 0.5f
  val parameters = args.foldLeft[Map[String,String]](Map()) {
    case (map,par) => {
      val split = par.split(" *= *", 2)
      if (split.length == 2) map + ((split(0).substring(1), split(1)))
      else map + ((split(0).substring(2), ""))
    }
  }
  val docIndexPath = parameters("docIndexPath")

  val sdIndexPath = parameters("sdIndexPath")
  val sdSearcher = new SimDocsSearch(sdIndexPath)

  val updAllDay = parameters("updAllDay").toInt
  val updateAll = ((updAllDay == 0) ||
                  (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == updAllDay))
  val idxFldName = Set("ti","ti_pt","ti_ru","ti_fr","ti_de","ti_it","ti_en",
                       "ti_es","ti_eng","ti_Pt","ti_Ru","ti_Fr","ti_De","ti_It",
                       "ti_En","ti_Es","ab_en","ab_es","ab_Es","ab_de","ab_De",
                       "ab_pt","ab_fr","ab_french", "ab")

  val docIndex = new DocsIndex(docIndexPath, sdSearcher)
  if (updateAll) docIndex.updateAllRecordDocs(idxFldName, minSim)
  else docIndex.updateNewRecordDocs(idxFldName, minSim)

  docIndex.close()
  sdSearcher.close()
}
