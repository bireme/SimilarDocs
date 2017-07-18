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

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/** Service that continuously updates all similar document ids (TopIndex)
*
* @param topDocs TopIndex Lucene object
* @author: Heitor Barbieri
* date: 20170524
*/
class UpdaterService(topDocs: TopIndex) {
  val WAIT_TIME = 1000 * 60 * 10 // 10 minutes
  var running = false

  /** Start the update of sd_id fields of new documents until the stop function
    * is called
    */
  def start(): Unit = {
//println("###'start' function called")
    running = true

    Future {
      while (running) {
        topDocs.updateSimilarDocs()
        if (running) Thread.sleep(WAIT_TIME)
      }
    }
  }

  /** Stop the service
    */
  def stop(): Unit = {
    running = false
    //Thread.sleep(WAIT_TIME)
  }
}
