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

import java.io.File
import java.util.{Calendar,GregorianCalendar,TimeZone}

import org.apache.lucene.document.DateTools
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.search.{IndexSearcher,TermRangeQuery}
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
    foreach(x => println(s"id:${x._1} entranceDate=${x._2}"))

  /**
    * Shows the ids of Lucene index documents that are younger or equals to 'days'
    *
    * @param indexName Lucene index path
    * @param days number of days from now used to filter the retrieved ids
    * @param maxDocs maximum number of document ids to be returned
    * @return a list of document (identifier,entranceDate)
    */
  def getNewDocsIds(indexName: String,
                    days: Int,
                    maxDocs: Int): Seq[(String,String)] = {
    require (indexName != null)
    require (days > 0)
    require (maxDocs > 0)

    val directory = FSDirectory.open(new File(indexName).toPath)
    val reader = DirectoryReader.open(directory)
    val searcher = new IndexSearcher(reader)

    val nowCal = new GregorianCalendar(TimeZone.getDefault)
    val today = DateTools.dateToString(DateTools.round(
      nowCal.getTime, DateTools.Resolution.DAY), DateTools.Resolution.DAY)
    val daysAgoCal = nowCal.clone().asInstanceOf[GregorianCalendar]
    daysAgoCal.add(Calendar.DAY_OF_MONTH, -days)  // begin of x days ago
    val daysAgo = DateTools.dateToString(daysAgoCal.getTime,
                                         DateTools.Resolution.DAY)
    val query = TermRangeQuery.newStringRange("entranceDate", daysAgo,
                                              today, true, true)
    val ids = searcher.search(query, maxDocs).scoreDocs.
                                         foldLeft[Seq[(String,String)]](Seq()) {
      case (seq,sd) =>
        val doc = reader.document(sd.doc, Set("id", "entranceDate").asJava)
        val id = doc.get("id")
        val entranceDate = doc.get("entranceDate")
        if (id == null) seq else seq :+ ((id, entranceDate))
    }
    reader.close()
    directory.close()

    ids
  }
}
