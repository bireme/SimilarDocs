/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/


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
  if (args.length != 3) usage()

  val dir = FSDirectory.open(new File(args(0)).toPath)
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
