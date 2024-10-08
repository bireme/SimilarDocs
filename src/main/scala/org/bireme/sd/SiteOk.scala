/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.sd

import org.apache.http.client.methods.{CloseableHttpResponse, HttpGet}
import org.apache.http.impl.client.{CloseableHttpClient, HttpClientBuilder}

object SiteOk extends App {
  def isSiteOn(url: String): Int = {
    val get: HttpGet = new HttpGet(url)
    val httpClient: CloseableHttpClient = HttpClientBuilder.create().build()
    val response: CloseableHttpResponse = httpClient.execute(get)
    val statusCode: Int = response.getStatusLine.getStatusCode

    httpClient.close()
println(s"statusCode=$statusCode")
    if (statusCode == 200) 1 else 0
  }

  private def usage(): Unit = {
    Console.err.println("usage: SiteOk <sitePath>")
    System.exit(1)
  }

  if (args.length != 1) usage()

  System.exit(isSiteOn(args(0)))
}
