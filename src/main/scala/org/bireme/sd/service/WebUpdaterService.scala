package org.bireme.sd.service

import sttp.client4.*
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.model.Uri

import java.net.http.HttpClient
import java.time.Duration

import scala.annotation.tailrec
import scala.concurrent.duration.*
import scala.util.{Try, Success, Failure}

object WebUpdaterService:

  private def usage(): Unit =
    System.err.println("usage: WebUpdaterService <SimDocs url>")
    System.exit(1)

  @main def run(args: String*): Unit =
    if args.length != 1 then usage()
    updateOneDocument(args.head)

  @tailrec
  def updateOneDocument(url: String): Unit =
    // Parse da URL + cria request GET com query param updateOneProfile=true
    val requestTry: Try[Request[Either[String, String]]] =
      Uri.parse(url).left.map(new IllegalArgumentException(_)).toTry.map { baseUri =>
        basicRequest
          .get(baseUri.addParam("updateOneProfile", "true"))
          .readTimeout(500000.millis)
      }

    requestTry match
      case Failure(e) =>
        println(s"[WebUpdaterService] Exception:${e.getMessage}")
        updateOneDocument(url)

      case Success(request) =>
        // Backend com connect timeout similar ao connTimeoutMs = 10000
        val httpClient =
          HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofMillis(10000))
            .build()

        val backend = HttpClientSyncBackend.usingClient(httpClient)

        val responseTry: Try[Response[Either[String, String]]] =
          Try(request.send(backend))

        responseTry match
          case Success(resp) =>
            if resp.code.code == 200 then
              val body: String = resp.body.fold(identity, identity)

              if body.startsWith("<finished/>") then
                println("[WebUpdaterService] All documents were updated")

              else if body.contains("maintenance_mode") then
                println("[WebUpdaterService] Waiting maintenance mode to finished")
                Thread.sleep(5L * 60L * 1000L)
                updateOneDocument(url)

              else if body.startsWith("<doc") then
                // Mantém o mesmo “substring(1, length-3)” do código original
                val msg = body.substring(1, body.length - 3)
                println(s"[WebUpdaterService] Updated [$msg]")
                updateOneDocument(url)

              else
                println(s"[WebUpdaterService] Unknown message error: [$body]")
            else
              println(s"[WebUpdaterService] SimilarDocs service error: ${resp.code.code}")

          case Failure(exception) =>
            println(s"[WebUpdaterService] Exception:${exception.getMessage}")
            updateOneDocument(url)
