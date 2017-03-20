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
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.store.FSDirectory
import scala.collection.JavaConverters._

/** Show a Lucene index document
  *
  * @author: Heitor Barbieri
  * date: 20170320
  *
*/
object ShowDocs extends App {
  private def usage(): Unit = {
    Console.err.println("usage: ShowDocs <indexName> <doc number>")
    System.exit(1)
  }

  if (args.length != 2) usage();

  showDocument(args(0), args(1))

  /**
    * Shows a Lucene index document
    *
    * @param indexName Lucene index path
    * @param docNum Lucene document number
    */
  def showDocument(indexName: String,
                   docNum: String): Unit = {
    val directory = FSDirectory.open(new File(indexName).toPath())
    val ireader = DirectoryReader.open(directory);
    val doc = ireader.document(docNum.toInt)

    println("----------------------------------------------------------")
    doc.getFields().asScala.foreach(field =>
      println(s"[${field.name}]=${field.stringValue()}"))
    //println(doc)
    println("----------------------------------------------------------")

    ireader.close()
    directory.close()
  }
}
