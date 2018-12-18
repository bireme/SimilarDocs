/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/


package org.bireme.sd.service

/** Service that updates all outdated similar document ids (TopIndex)
*
* @param topDocs TopIndex Lucene object
*
* author: Heitor Barbieri
* date: 20170524
*/
class UpdaterServiceXX(topDocs: TopIndex) {
  var running = false         // there are an updating running
  var stopping = false        // the system wants to exit
  var force = false           // force exit

  /** Update the similar docs of all documents that are outdated
    * @return true if the all updates were succeeded and false if it failed
    */
  private def updateAll(): Boolean = {
    if (force || stopping) false
    else {
      topDocs.updateSimilarDocs() match {
        case Some(_) => updateAll() // has more documents to update
        case None    => true
      }
    }
  }

  /** Update the similar docs of all documents that are outdated
    * @return true if the start was succeeded and false if it failed
    */
  def start(): Boolean = {
    if (force || stopping) false
    else if (running) true
    else {
      running = true
      val ret = updateAll()
      running = false
      ret
    }
  }

  /** Stop the service
    * @return true if the stop was succeeded and false if it failed
    */
  def stop(): Boolean = {
    if (force) true
    else if (running) {
      stopping = true
      Thread.sleep(5000)  // wait 05 seconds
      if (running) {
        stopping = false
        false
      } else {
        topDocs.resetAllTimes()
        stopping = false
        true
      }
    } else true
  }

  /**
    * Force the stop of all updates
    */
  def stopForce(): Unit = {
    force = true
    Thread.sleep(5000)  // wait 05 seconds
    topDocs.resetAllTimes()
  }
}
