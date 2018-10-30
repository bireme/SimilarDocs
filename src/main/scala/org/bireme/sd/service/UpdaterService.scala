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
class UpdaterService(topDocs: TopIndex) {
  var stopping = false        // the system wants to exit

  /** Update the similar docs of all documents that are outdated
    * @return true if the all updates were succeeded and false if it failed
    */
  private def updateAll(): Boolean = {
    if (stopping) false
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
  def start(): Boolean = (!stopping) && updateAll()

  /** Stop the service
    * @return true if the stop was succeeded and false if it failed
    */
  def stop(): Boolean = {
    stopping = true
    Thread.sleep(10000)  // wait 10 seconds
    topDocs.resetAllTimes()
    stopping = false
    true
  }
}
