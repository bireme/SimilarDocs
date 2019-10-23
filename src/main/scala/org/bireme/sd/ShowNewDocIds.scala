/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/


package org.bireme.sd

import java.io.File
import java.util.{Calendar, GregorianCalendar, TimeZone}

import org.apache.lucene.document.{DateTools, Document}
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.search.{IndexSearcher, TermRangeQuery}
import org.apache.lucene.store.FSDirectory

import scala.collection.JavaConverters._

/** Show a Lucene index document
  *
  * author: Heitor Barbieri
  * date: 20171101
  *
*/
object ShowNewDocIds extends App {
  private def usage(): Unit = {
    Console.err.println("usage: ShowNewDocIds <indexName> <days> [<max number of documents>]")
    System.exit(1)
  }

  if (args.length < 2) usage()

  val maxDocs = if (args.length == 2) 1000 else args(2).toInt
  getNewDocsIds(args(0), args(1).toInt, maxDocs).
    foreach(x => println(s"id:${x._1} entrance_date=${x._2}"))

  /**
    * Shows the ids of Lucene index documents that are younger or equals to 'days'
    *
    * @param indexName Lucene index path
    * @param days number of days from now used to filter the retrieved ids
    * @param maxDocs maximum number of document ids to be returned
    * @return a list of document (identifier,entrance_date)
    */
  def getNewDocsIds(indexName: String,
                    days: Int,
                    maxDocs: Int): Seq[(String,String)] = {
    require (indexName != null)
    require (days > 0)
    require (maxDocs > 0)

    val directory: FSDirectory = FSDirectory.open(new File(indexName).toPath)
    val reader: DirectoryReader = DirectoryReader.open(directory)
    val searcher: IndexSearcher = new IndexSearcher(reader)

    val nowCal: GregorianCalendar = new GregorianCalendar(TimeZone.getDefault)
    val today: String = DateTools.dateToString(DateTools.round(
      nowCal.getTime, DateTools.Resolution.DAY), DateTools.Resolution.DAY)
    val daysAgoCal: GregorianCalendar = nowCal.clone().asInstanceOf[GregorianCalendar]
    daysAgoCal.add(Calendar.DAY_OF_MONTH, -days)  // begin of x days ago
    val daysAgo: String = DateTools.dateToString(daysAgoCal.getTime,
                                         DateTools.Resolution.DAY)
    val query: TermRangeQuery = TermRangeQuery.newStringRange("entrance_date", daysAgo,
                                              today, true, true)
    val ids: Seq[(String, String)] = searcher.search(query, maxDocs).scoreDocs.
                                         foldLeft[Seq[(String,String)]](Seq()) {
      case (seq,sd) =>
        val doc: Document = reader.document(sd.doc, Set("id", "entrance_date").asJava)
        val id: String = doc.get("id")
        val entrance_date: String = doc.get("entrance_date")
        if (id == null) seq else seq :+ ((id, entrance_date))
    }
    reader.close()
    directory.close()

    ids
  }
}
