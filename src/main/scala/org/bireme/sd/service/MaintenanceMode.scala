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

import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
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
  val url = s"$url2?maintenance=$mode"
  val get = new HttpGet(url)
  get.setHeader("Content-type", "text/plain;charset=utf-8")

  val httpClient = HttpClientBuilder.create().build()
  val response = httpClient.execute(get)
  val statusCode = response.getStatusLine.getStatusCode
  if (statusCode == 200)
    println(EntityUtils.toString(response.getEntity))
  else throw new Exception(s"url=$url statusCode=$statusCode")

  httpClient.close()
}
