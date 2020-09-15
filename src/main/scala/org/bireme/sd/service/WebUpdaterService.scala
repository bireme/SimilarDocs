/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.sd.service

import scalaj.http.{Http, HttpResponse}

import scala.util.{Failure, Success, Try}

object WebUpdaterService extends App {
  private def usage(): Unit = {
    System.err.println("usage: WebUpdaterService <SimDocs url>")
    System.exit(1)
  }

  if (args.length != 1) usage()

  updateOneDocument(args(0))


  @scala.annotation.tailrec
  def updateOneDocument(url: String): Unit = {
    val response: Try[HttpResponse[String]] = Try (Http(url).timeout(connTimeoutMs = 10000, readTimeoutMs = 500000)
                                                  .param("updateOneProfile","true").asString)
    response match {
      case Success(resp) =>
        if (resp.code == 200) {
          val body: String = resp.body
          if (body.startsWith("<finished/>")) {
            println(s"[WebUpdaterService] All documents were updated")
          } else if (body.contains("maintenance_mode")) {
            println("[WebUpdaterService] Waiting maintenance mode to finished")
            Thread.sleep(5 * 60 * 1000)
            updateOneDocument(url)
          } else if (body.startsWith("<doc")) {
            val msg = body.substring(1, body.length - 3)
            println(s"[WebUpdaterService] Updated [$msg]")
            updateOneDocument(url)
          } else println(s"[WebUpdaterService] Unknown message error: [$body]")
        } else println(s"[WebUpdaterService] SimilarDocs service error: ${resp.code}")
      case Failure(exception) =>
        println(s"[WebUpdaterService] Exception:${exception.getMessage}")
        updateOneDocument(url)
    }
  }
}

