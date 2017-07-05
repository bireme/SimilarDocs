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

import org.apache.lucene.document.{Document,Field,TextField}
import org.apache.lucene.index.{IndexWriter,IndexWriterConfig}
import org.apache.lucene.store.FSDirectory

/** Creates a Lucene index from a set of xml document files
*
* @author: Heitor Barbieri
* date: 20170102
*/
object LuceneIndex extends App {
  private def usage(): Unit = {
    Console.err.println("usage: LuceneIndex" +
      "\n\t<indexPath> - path to Lucene index to be created" +
      "\n\t<xmlDir> - directory of xml used to populate the index" +
      "\n\t[-fields=<field1>,...,<fieldN>] - field names that will be indexed" +
      "\n\t[-encoding=<str>] - character encoding from xml files")
    System.exit(1)
  }

  if (args.length < 2) usage()

  val parameters = args.drop(2).foldLeft[Map[String,String]](Map()) {
    case (map,par) => {
      val split = par.split(" *= *", 2)
      map + ((split(0).substring(1), split(1)))
    }
  }
  val sFields = parameters.getOrElse("fields", "")
  val fldNames = (if (sFields.isEmpty) Set[String]()
                 else sFields.split(" *, *").toSet) + "id"

  val encoding = parameters.getOrElse("encoding", "ISO-8859-1")

  index(args(0), args(1), fldNames, encoding)

  def index(indexPath: String,
            xmlDir: String,
            fldNames: Set[String],
            encoding: String): Unit = {
    val analyzer = new NGramAnalyzer(NGSize.ngram_size)
    val directory = FSDirectory.open(new File(indexPath).toPath())
    val config = new IndexWriterConfig(analyzer)
    config.setOpenMode(IndexWriterConfig.OpenMode.CREATE)
    val writer = new IndexWriter(directory, config)

    (new File(xmlDir)).listFiles().foreach {
      file =>
        val xmlFile = file.getPath()
        if (xmlFile.toLowerCase().endsWith(".xml")) {
          println(s"\nindexing file: $xmlFile")
          indexFile(writer, xmlFile, fldNames, encoding)
        }
    }

    print("\nOptimizing index ...")
    writer.forceMerge(1)
    writer.close()
    directory.close()
  }

  def indexFile(indexWriter: IndexWriter,
                xmlFile: String,
                fldNames: Set[String],
                encoding: String): Unit = {
    IahxXmlParser.getElements(xmlFile, encoding, Set()).zipWithIndex.foreach {
      case (map,idx) =>
        if (idx % 5000 == 0) print(".")
        indexWriter.addDocument(map2doc(map.toMap, fldNames))
    }
  }

  def map2doc(map: Map[String,List[String]],
              fldNames: Set[String]): Document = {
    val doc = new Document()

    map.foreach {
      case (tag,lst) =>
        if (fldNames(tag)) {
          lst.foreach {
            elem => doc.add(new TextField(tag, elem,  Field.Store.YES))
          }
        } else {
          /*lst.foreach {
            elem => doc.add(new StoredField(tag, elem))
          }*/
        }
    }
    doc
  }
}
