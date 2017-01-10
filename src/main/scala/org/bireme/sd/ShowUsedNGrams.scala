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
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.FSDirectory

import org.apache.lucene.analysis.{Analyzer,TokenStream}
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute

import scala.collection.immutable.TreeMap

object ShowUsedNGrams extends App {
  private def usage(): Unit = {
    Console.err.println("usage: ShowUsedNGrams <index> <field>,...,<field> " +
                                               "<text> <similar> [<ngramsize>]")
    System.exit(1)
  }

  if (args.length < 4) usage()

  val fields = args(1).trim.split(" *\\, *").toSet
  val size = if (args.length > 4) args(4).toInt else NGSize.ngram_size

  show(args(2), args(3), fields, args(0), size)

  def show(text: String,
           similar: String,
           fields: Set[String],
           index: String,
           size: Int): Unit = {
    val directory = FSDirectory.open(new File(index).toPath())
    val reader = DirectoryReader.open(directory)
    val searcher = new IndexSearcher(reader)
    val analyzer = new NGramAnalyzer(size)
    val map1 = getIndexNGrams(similar, fields, searcher, analyzer)
    val map2 = getNGrams(text, analyzer)
    val map3 = getIndexNGrams(text, fields, searcher, analyzer)

    println(s"\n\n$similar")
    print("\nngrams indexed: ")
    map1.foreach {
      case (k,v) => print(s"$k[$v] ")
    }

    println(s"\n$text")
    print("\nngrams generated: ")
    map2.foreach {
      case (k,v) => print(s"$k[$v] ")
    }
    print("\nngrams in index: ")
    map3.foreach {
      case (k,v) => print(s"$k[$v] ")
    }
    print("\ncommon ngrams: ")
    map3.filter {
      case (k,_) => map1.contains(k)
    }.foreach {
      case (k,v) => print(s"$k[$v] ")
    }
    println("\n")

    reader.close()
    directory.close()
  }

  def getIndexNGrams(text: String,
                     fields: Set[String],
                     searcher: IndexSearcher,
                     analyzer: Analyzer): Map[String,Int] = {
    getNGrams(text, analyzer).filter {
      case (tok,qtt) => hasToken(tok,fields,searcher,analyzer)
    }
  }

  private def getNGrams(text: String,
                        analyzer: Analyzer): Map[String,Int] = {
    val tokenStream = analyzer.tokenStream(null, text)
    val cattr = tokenStream.addAttribute(classOf[CharTermAttribute])

    tokenStream.reset()
    val map = getTokens(tokenStream, cattr, TreeMap[String,Int]())
//println(s"\ntext=$text  map=$map\n")
    tokenStream.end()
    tokenStream.close()

    map
  }

  private def getTokens(tokenStream: TokenStream,
                        cattr: CharTermAttribute,
                        auxMap: Map[String,Int]): Map[String,Int] = {
    if (tokenStream.incrementToken()) {
      val tok = cattr.toString()
      val occ = auxMap.getOrElse(tok, 0)
      getTokens(tokenStream, cattr, auxMap + ((tok,occ+1)))
    } else auxMap
  }

  private def hasToken(tok: String,
                       fields: Set[String],
                       searcher: IndexSearcher,
                       analyzer: Analyzer): Boolean = {
    val mqParser = new MultiFieldQueryParser(fields.toArray, analyzer)
//println(s"tok=$tok")
    if (tok.contains("(") || tok.contains(")") || tok.contains(":")) false else {
      val query =  mqParser.parse(tok)

      searcher.search(query, 1).totalHits > 0
    }
  }
}
