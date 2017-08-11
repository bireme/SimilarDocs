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

/** Service that updates all outdated similar document ids (TopIndex)
*
* @param topDocs TopIndex Lucene object
* @author: Heitor Barbieri
* date: 20170524
*/
class UpdaterService(topDocs: TopIndex) {
  var finished = false
  var running = true

  /** Update the similar docs of all documents that are outdated
    */
  def updateAll(): Unit = {
    if (running) topDocs.updateSimilarDocs() match {
      case Some(_) => updateAll()
      case None => {
        finished = true
        running = false
      }
    } else finished = true
  }

  /** Update the similar docs of all documents that are outdated
    */
  def start(): Unit = {
    finished = false
    running = true
    updateAll()
  }

  /** Stop the service
    */
  def stop(): Unit = {
    running = false
    while (!finished) Thread.sleep(1000)
    topDocs.resetAllTimes()
  }

  // Outdate all documents
  //topDocs.resetAllTimes()
}
