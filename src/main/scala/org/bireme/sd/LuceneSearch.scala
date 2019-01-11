package org.bireme.sd

import java.io.File

import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.{IndexSearcher, Query}
import org.apache.lucene.store.FSDirectory
import org.bireme.sd.service.Conf

object LuceneSearch extends App{
  private def usage(): Unit = {
    System.err.println("usage: LuceneSearch <indexPath> <bool>")
    System.exit(1)
  }

  if (args.length != 2) usage()

  val sdDirectory: FSDirectory = FSDirectory.open(new File(args(0)).toPath)
  val dirReader: DirectoryReader = DirectoryReader.open(sdDirectory)
  val searcher = new IndexSearcher(dirReader)

  val qParser: QueryParser = new QueryParser(Conf.indexedField, new NGramAnalyzer(NGSize.ngram_min_size, NGSize.ngram_max_size))
  val query: Query = qParser.parse(args(1))

  searcher.search(query, 20).scoreDocs.foreach {
    scoreDoc => println(s"doc=${scoreDoc.doc} score=${scoreDoc.score}")
  }

  dirReader.close()
  sdDirectory.close()
}
