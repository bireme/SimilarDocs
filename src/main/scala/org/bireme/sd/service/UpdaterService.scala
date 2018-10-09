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

//import scala.concurrent.{Await,Future}
//import scala.concurrent.ExecutionContext.Implicits.global
//import scala.concurrent.duration._

/** Service that updates all outdated similar document ids (TopIndex)
*
* @param topDocs TopIndex Lucene object
*
* author: Heitor Barbieri
* date: 20170524
*/
class UpdaterService(topDocs: TopIndex) {
  var running = false
  var stopping = false

  /** Update the similar docs of all documents that are outdated
    */
  private def updateAll(): Unit = {
    if (!stopping) topDocs.updateSimilarDocs() match {
      case Some(_) => updateAll()  // has more documents to update
      case None => ()
    }
  }

  /** Update the similar docs of all documents that are outdated
    */
  def start(): Unit = {
    if (!running) {
      while (stopping) Thread.sleep(500)
      running = true
      updateAll()
      running = false

      /*val updAll = Future { updateAll() }
      val result = Await.ready(updAll, Duration.Inf).value.get

      result match {
        case _ => running = false
      }*/
    }
  }

  /** Stop the service
    */
  def stop(): Unit = {
    stopping = true
    while (running) Thread.sleep(500)
    topDocs.resetAllTimes()
    stopping = false
  }
}
