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

package org.bireme.sd

import com.mongodb.ServerAddress
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.MongoClientOptions

import java.io.File
import java.nio.file.{Files,Paths}
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.{Calendar,Date,GregorianCalendar,TimeZone}

import play.api.libs.json._

/** Export all documents from a MongoDb collection to a file
*
* @param host MongoDb server host
* @param port MongoDb server port
*
* @author Heitor Barbieri
* date: 20170824
*/
class MongoDbExport(host: String,
                    port: Int = 27017) {

  /**
    * Export all documents between a period of time to a file. The documents
    * creation date should br stored in the 'timestamp' field.
    *
    * @param dataBase MongoDB dataBase
    * @param collection MongoDB collection
    * @param beginDate initial date of documents to be exported (included)
    * @param endDate end date of documents to be exported (excluded)
    * @param outFile file name where the documents will be exported
    * @param prettyPrint if true the output json file will be formatted
    */
  def exportDocuments(dataBase: String,
                      collection: String,
                      beginDate: Date,
                      endDate: Date,
                      outFile: String,
                      prettyPrint: Boolean = false): Unit = {
    require(dataBase != null)
    require(collection != null)
    require(outFile != null)

    val options = MongoClientOptions(connectTimeout = 60000)
    //val mongoClient = MongoClient(host, port)
    val mongoClient = MongoClient(new ServerAddress(host, port), options)
    val dbase = mongoClient(dataBase)
    val coll = dbase(collection)
    val docs = if (beginDate == null) coll.find() else {
//println(s"beginDate=$beginDate endDate=$endDate")
      val between = MongoDBObject("$gte" -> beginDate, "$lt" -> endDate)
      val query = MongoDBObject("timestamp" -> between)
      coll.find(query)
    }
    val writer = Files.newBufferedWriter(Paths.get(outFile),
                                         Charset.forName("utf-8"))
    var first = true

    writer.write("{\"docs\": [\n")

    docs.foreach {
      doc =>
        val json = Json.parse(doc.toString)
        val jsonStr = if (prettyPrint) Json.prettyPrint(json)
                      else Json.stringify(json)
        if (first) first = false
        else writer.write(",\n")
        writer.write(jsonStr)
    }

    writer.write("\n]}")
    writer.close()
    mongoClient.close()
  }

  /**
    * @return the date of the initial instant of yesterday and the date of the
    *         initial instant of today
    */
  private def getYesterdayDate: (Date, Date) = {
    val now = new GregorianCalendar(TimeZone.getDefault)
    val year = now.get(Calendar.YEAR)
    val month = now.get(Calendar.MONTH)
    val day = now.get(Calendar.DAY_OF_MONTH)
    val todayCal = new GregorianCalendar(year, month, day, 0, 0) // begin of today
    val today = todayCal.getTime                                 // begin of today date
    val yesterdayCal = todayCal.clone().asInstanceOf[GregorianCalendar]
    yesterdayCal.add(Calendar.DAY_OF_MONTH, -1)                  // begin of yesterday
    val yesterday = yesterdayCal.getTime                         // begin of yesterday date

    (yesterday, today)
  }
}

/** Export all documents from a MongoDb collection to a file
  */
object MongoDbExport extends App {
  private def usage(): Unit = {
    Console.err.println("usage: MongoDbExport" +
    "\n\t-host=<host> - MongoDB host"+
    "\n\t-dbase=<database> - MongoDB database name" +
    "\n\t-coll=<collection> - MongoDB collection name" +
    "\n\t-outFileDir=<dir> - diretory into which the output file will be written" +
    "\n\t[-port=<port>] - MongoDB server port" +
    "\n\t[--exportAll] - if presente all log documents will be exported if not only the ones created yesterday" +
    "\n\t[--prettyPrint] - the json exported documents will be formatted (pretty print)")
    System.exit(1)
  }

  if (args.length < 3) usage()

  val parameters = args.foldLeft[Map[String,String]](Map()) {
    case (map,par) =>
      val split = par.split(" *= *", 2)
      if (split.size == 1) map + ((split(0).substring(2), ""))
      else map + ((split(0).substring(1), split(1)))
  }
  val host = parameters("host")
  val dbase = parameters("dbase")
  val coll = parameters("coll")
  val outFileDir = parameters("outFileDir")
  val port = parameters.getOrElse("port", "27017").toInt
  val exportAll = parameters.contains("exportAll")
  val prettyPrint = parameters.contains("prettyPrint")
  val mongoExp = new MongoDbExport(host, port)
  val (yesterday, today) = mongoExp.getYesterdayDate
  val format = new SimpleDateFormat("yyyyMMdd")
  val outFileName = if (exportAll) s"begin_${format.format(today)}.json"
    else s"${format.format(yesterday)}.json"
  val outFile = new File(outFileDir, outFileName).getPath

  if (exportAll)
    mongoExp.exportDocuments(dbase, coll, null, null, outFile, prettyPrint)
  else
    mongoExp.exportDocuments(dbase, coll, yesterday, today, outFile, prettyPrint)
}
