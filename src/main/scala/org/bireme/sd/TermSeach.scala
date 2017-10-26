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

//import org.apache.lucene.index.{DirectoryReader,Term}
import org.apache.lucene.search.{IndexSearcher,TermQuery}
import org.apache.lucene.store.FSDirectory

import org.apache.lucene.index.{DirectoryReader,IndexWriter,IndexWriterConfig,Term}
import org.apache.lucene.analysis.core.KeywordAnalyzer

object TermSearch extends App {
  private def usage(): Unit = {
    Console.err.println("usage: TermSearch <indexPath> <field> <value>")
    System.exit(1)
  }
  if (args.size != 3) usage()

  val dir = FSDirectory.open(new File(args(0)).toPath())
  //val reader = DirectoryReader.open(dir)

  val config = new IndexWriterConfig(new KeywordAnalyzer)
  config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND)
  val indexWriter = new IndexWriter(dir, config)

  val reader = DirectoryReader.open(indexWriter)
  val searcher = new IndexSearcher(reader)

  val query = new TermQuery(new Term(args(1), args(2)))

  val docs = searcher.search(query, Integer.MAX_VALUE)

  println(s"Hits: ${docs.totalHits}")

  reader.close()
}
