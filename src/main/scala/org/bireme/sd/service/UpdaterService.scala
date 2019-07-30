/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.sd.service

import org.bireme.sd.SimDocsSearch

/** Service that updates all outdated similar document ids (TopIndex)
*
* @param topDocs TopIndex Lucene object
*
* author: Heitor Barbieri
* date: 20170524
*/
class UpdaterService(topDocs: TopIndex) {
  private var stopping = false        // if the system wants to exit

  /** Update the similar docs of all documents that are outdated
 *
    * @return true if the all updates were succeeded and false if it failed
    */
  @scala.annotation.tailrec
  private def updateAll(): Boolean = {
    if (stopping) false
    else {
      topDocs.updateSimilarDocs(Conf.maxDocs, Conf.lastDays, Conf.sources, Conf.instances) match {
        case Some(_) => updateAll() // has more documents to update
        case None    => true
      }
    }
  }

  /** Update the similar docs of all documents that are outdated
    * @return true if the start was succeeded and false if it failed
    */
  def start(): Boolean = {
    print("Reseting all times ...")
    topDocs.resetAllTimes()
    print(" OK.\nUpdating all similar documents ...")
    val result = updateAll()
    stopping = result
    println(" OK")
    result
  }

  /** Stop the service
    * @return true if the stop was succeeded and false if it failed
    */
  def stop(): Boolean = {
    stopping = true
    true
  }
}

object UpdaterService extends App {
  private def usage(): Unit = {
    System.err.println("usage: UpdaterService -sdIndex=<path> -decsIndex=<path> -topIndex=<path> (--start|--stop)")
    System.exit(1)
  }

  if (args.length != 4) usage()

  val parameters = args.foldLeft[Map[String,String]](Map()) {
    case (map,par) =>
      val split = par.split(" *= *", 2)
      if (split.length == 2) map + ((split(0).substring(1), split(1)))
      else map + ((split(0).substring(2), ""))
  }

  val sdIndex: String = parameters("sdIndex")
  val decsIndex: String = parameters("decsIndex")
  val topIndex: String = parameters("topIndex")

  val op = if (parameters.contains("start")) "start"
           else if (parameters.contains("stop")) "stop"
           else ""

  val sim = new SimDocsSearch(sdIndex, decsIndex)
  val top = new TopIndex(sim, topIndex)
  val upds = new UpdaterService(top)

  op match {
    case "start" => upds.start()
    case "stop" => upds.stop()
    case _ => usage()
  }

  top.close()
  sim.close()
}
