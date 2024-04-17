/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/


package org.bireme.sd.service

import java.util.concurrent.TimeUnit

import org.apache.http.client.methods.{CloseableHttpResponse, HttpGet}
import org.apache.http.impl.client.{CloseableHttpClient, HttpClientBuilder}
import org.apache.http.util.EntityUtils

/** Application to set or reset the SDService maintenance mode flag
*
* author: Heitor Barbieri
* date: 20170323
*/
object MaintenanceMode extends App {
  private def usage(): Unit = {
    Console.err.println("usage: MaintenanceMode:\n" +
      "\n\t<SDService url>: SDService url. For ex: http://serverofi5.bireme.br:8080/SDService/SDService" +
      "\n\t<[re]set> : changes maintenance mode flag to 'off' or 'on'"
    )
    System.exit(1)
  }

  if (args.length != 2) usage()

  var mode:String = ""

  if (args(1).equals("set")) mode = "true"
  else if (args(1).equals("reset")) mode = "false"
  else usage()

  val url1 = args(0).trim
  val url2 = if (url1.endsWith("/")) url1.substring(0,url1.lastIndexOf('/'))
             else url1
  val url: String = s"$url2?maintenance=$mode"
  val get: HttpGet = new HttpGet(url)
  get.setHeader("Content-type", "text/plain;charset=utf-8")

  val httpClient: CloseableHttpClient = HttpClientBuilder.create().setConnectionTimeToLive(1, TimeUnit.HOURS).build()
  val response: CloseableHttpResponse = httpClient.execute(get)
  val statusCode: Int = response.getStatusLine.getStatusCode
  if (statusCode == 200) {
    val result = EntityUtils.toString(response.getEntity)

    if (result.contains("FAILED")) throw new Exception(s"url=$url statusCode=500")
    else println(result)
  } else throw new Exception(s"url=$url statusCode=$statusCode")

  httpClient.close()
}
