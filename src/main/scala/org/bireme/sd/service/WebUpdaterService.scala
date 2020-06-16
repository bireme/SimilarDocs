/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.sd.service

import scalaj.http.{Http, HttpResponse}

object WebUpdaterService extends App {
  private def usage(): Unit = {
    System.err.println("usage: WebUpdaterService <SimDocs url>")
    System.exit(1)
  }

  if (args.length != 1) usage()

  updateOneDocument(args(0))


  @scala.annotation.tailrec
  def updateOneDocument(url: String): Unit = {
    val response: HttpResponse[String] = Http(url).param("updateOneProfile","true").asString
    if (response.code != 200) throw new Exception(s"SimilarDocs service error: ${response.code}")

    val body: String = response.body
    if (body.equals("finished")) {
      println(s"[WebUpdaterService] All documents were updated")
    } else if (body.contains("maintenance mode")) {
      println("[WebUpdaterService] Waiting maintenance mode to finished")
      Thread.sleep(5 * 60 * 1000)
    } else {
      println(s"[WebUpdaterService] Updated document id:$body")
      updateOneDocument(url)
    }
  }
}
