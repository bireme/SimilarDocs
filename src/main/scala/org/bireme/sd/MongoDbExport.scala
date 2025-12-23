/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.sd

import com.google.gson.{GsonBuilder, JsonParser}
import java.io.File
import java.nio.charset.Charset
import java.nio.file.{Files, Paths}
import java.text.SimpleDateFormat
import java.util.{Calendar, Date, GregorianCalendar, TimeZone}

// Java sync driver imports
import com.mongodb.client.{FindIterable, MongoClient, MongoClients, MongoCollection, MongoDatabase}
import org.bson.Document

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
                      beginDate: Option[Date],
                      endDate: Option[Date],
                      outFile: String,
                      prettyPrint: Boolean = false): Unit = {
    require(dataBase != null)
    require(collection != null)
    require(outFile != null)

    val mongoClient: MongoClient = MongoClients.create(s"mongodb://$host:$port")
    val dbase: MongoDatabase = mongoClient.getDatabase(dataBase)
    val coll: MongoCollection[Document] = dbase.getCollection(collection)

    val docs: FindIterable[Document] = beginDate match {
      case Some(begDate) =>
        val timestamp: Document =
          endDate match {
            case Some(ed) => new Document("$gte", begDate).append("$lt", ed)
            case None     => new Document("$gte", begDate)
          }
        val query: Document = new Document("timestamp", timestamp)
        coll.find(query)
      case None =>
        coll.find()
    }

    val writer = Files.newBufferedWriter(
      Paths.get(outFile),
      Charset.forName("utf-8")
    )

    val gson = new GsonBuilder().setPrettyPrinting().create()
    var first = true

    writer.write("{\"docs\": [\n")

    val it = docs.iterator()
    try {
      while (it.hasNext) {
        val doc: Document = it.next()
        val docStr: String = doc.toJson() // no driver Java, use toJson()
        val jsonStr: String =
          if (prettyPrint) gson.toJson(JsonParser.parseString(docStr))
          else docStr

        if (first) first = false
        else writer.write(",\n")

        writer.write(jsonStr)
      }
    } finally {
      it.close() // fecha o cursor
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
      "\n\t-host=<host> - MongoDB host" +
      "\n\t-dbase=<database> - MongoDB database name" +
      "\n\t-coll=<collection> - MongoDB collection name" +
      "\n\t-outFileDir=<dir> - diretory into which the output file will be written" +
      "\n\t[-port=<port>] - MongoDB server port" +
      "\n\t[--exportAll] - if presente all log documents will be exported if not only the ones created yesterday" +
      "\n\t[--prettyPrint] - the json exported documents will be formatted (pretty print)")
    System.exit(1)
  }

  if (args.length < 3) usage()

  private val parameters = args.foldLeft[Map[String, String]](Map()) {
    case (map, par) =>
      val split = par.split(" *= *", 2)
      if (split.size == 1) map + ((split(0).substring(2), ""))
      else map + ((split(0).substring(1), split(1)))
  }

  private val host = parameters("host")
  private val dbase = parameters("dbase")
  private val coll = parameters("coll")
  private val outFileDir = parameters("outFileDir")
  private val port = parameters.getOrElse("port", "27017").toInt
  private val exportAll = parameters.contains("exportAll")
  private val prettyPrint = parameters.contains("prettyPrint")
  private val mongoExp = new MongoDbExport(host, port)

  private val (yesterday, today) = mongoExp.getYesterdayDate
  private val format = new SimpleDateFormat("yyyyMMdd")
  private val outFileName =
    if (exportAll) s"begin_${format.format(today)}.json"
    else s"${format.format(yesterday)}.json"

  private val outFile = new File(outFileDir, outFileName).getPath

  if (exportAll)
    mongoExp.exportDocuments(dbase, coll, None, None, outFile, prettyPrint)
  else
    mongoExp.exportDocuments(dbase, coll, Some(yesterday), Some(today), outFile, prettyPrint)
}
