/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/


package org.bireme.sd

import java.io.File

import org.apache.lucene.document.Document
import org.apache.lucene.index.{DirectoryReader, IndexReader, MultiBits}
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.Bits

//import scala.collection.JavaConverters._
import scala.jdk.CollectionConverters._

/** Show a Lucene index document
  *
  * author: Heitor Barbieri
  * date: 20170320
  *
*/
object ShowDocs extends App {
  private def usage(): Unit = {
    Console.err.println("usage: ShowDocs <indexName> [<doc number>]")
    System.exit(1)
  }

  if (args.length < 1) usage()

  private val docNum = if (args.length == 1) "" else args(1)
  showDocument(args(0), docNum)

  /**
    * Shows a Lucene index document
    *
    * @param indexName Lucene index path
    * @param docNum Lucene document number
    */
  private def showDocument(indexName: String,
                           docNum: String): Unit = {
    val directory: FSDirectory = FSDirectory.open(new File(indexName).toPath)
    val ireader: DirectoryReader = DirectoryReader.open(directory)
    //val liveDocs: Bits = MultiFields.getLiveDocs(ireader) // Lucene version before 8.0.0
    val liveDocs: Bits = MultiBits.getLiveDocs(ireader) // Lucene version 8.0.0

    if (docNum.isEmpty) (0 until ireader.numDocs()).foreach(showDoc(ireader, _, liveDocs))
    else showDoc(ireader, docNum.toInt, liveDocs)

    ireader.close()
    directory.close()
  }

  /**
    * Shows a Lucene index document
    *
    * @param ireader Lucene index reader
    * @param docNum Lucene document number
    */
  private def showDoc(ireader: IndexReader,
                      docNum: Int,
                      liveDocs: Bits): Unit = {
    val doc: Document = ireader.storedFields().document(docNum) //ireader.document(docNum)
    val active: Boolean = (liveDocs == null) || liveDocs.get(docNum)
    val header: String = if (active) s" id=$docNum " else s" id=$docNum  [DELETED] "

    println(s"\n---------$header-------------------------------------")
    doc.getFields().asScala.foreach(field => println(s"[${field.name}]=${field.stringValue()}"))
  }
}
