/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/


package org.bireme.sd

import java.io.File

import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.FSDirectory

import org.apache.lucene.analysis.{Analyzer,TokenStream}
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute

import scala.collection.immutable.TreeMap

/** Application that shows all used ngrams used in original text and in the
  * similar retrieved one
  */
object ShowUsedNGrams extends App {
  private def usage(): Unit = {
    Console.err.println("usage: ShowUsedNGrams" +
      "\n\t<index> - Lucene index path used to look for similar documents" +
      "\n\t<field>,...,<field> - document field names used to look for text similarities" +
      "\n\t<text> - original text used to look for similar documents" +
      "\n\t<similar> - similar document content")
    System.exit(1)
  }

  if (args.length < 4) usage()

  val fields = args(1).trim.split(" *\\, *").toSet
  val minSize = NGSize.ngram_min_size
  val maxSize = NGSize.ngram_max_size

  show(args(2), args(3), fields, args(0), minSize, maxSize)

  def show(text: String,
           similar: String,
           fields: Set[String],
           index: String,
           minSize: Int,
           maxSize: Int): Unit = {
    val directory = FSDirectory.open(new File(index).toPath)
    val reader = DirectoryReader.open(directory)
    val searcher = new IndexSearcher(reader)
    val analyzer = new NGramAnalyzer(minSize, maxSize)
    val map1 = getIndexNGrams(similar, fields, searcher, analyzer)
    val map2 = getNGrams(text, analyzer)
    val map3 = getIndexNGrams(text, fields, searcher, analyzer)

    // All ngrams of similar text that are in the fields of the index
    println(s"\n\n$similar")
    print("\nngrams indexed: ")
    map1.foreach {
      case (k,v) => print(s"$k[$v] ")
    }
    // All ngrams present in the input text
    println(s"\n$text")
    print("\nngrams generated: ")
    map2.foreach {
      case (k,v) => print(s"$k[$v] ")
    }
    // All ngrams of input text that are in the fields of the index
    print("\nngrams in index: ")
    map3.foreach {
      case (k,v) => print(s"$k[$v] ")
    }
    // All ngrams that are present in both input text and similar one
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

  /**
    * Retrieves all ngrams of a text that are also present into document fields
    *
    * @param text input text used to extract ngrams
    * @param fields fields used to check for ngrams presence
    * @param searcher Lucene IndexSearcher object. See Lucene documentation
    * @param analyzer Lucene Analyzer object. See Lucene documentation
    * @return a map of ngrams and its associated number of occurrences
    */
  def getIndexNGrams(text: String,
                     fields: Set[String],
                     searcher: IndexSearcher,
                     analyzer: Analyzer): Map[String,Int] = {
    getNGrams(text, analyzer).filter {
      case (tok,_) => hasToken(tok,fields,searcher,analyzer)
    }
  }

  /**
    * Calculates all tokens of a string and its associated occurrences
    *
    * @param text input text used to extract ngrams
    * @param analyzer Lucene analyzer object. See Lucene documentation
    * @return a map of ngrams and its number of occurrences
    */
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

  /**
    * Calculates all tokens of a token stream and its associated occurrences
    *
    * @param tokenStream Lucene token stream object
    * @param cattr Lucene CharTermAttribute object. See Lucene documentation
    * @param auxMap auxiliary working map object
    * @return a map of tokens and its number of occurrences
    */
  private def getTokens(tokenStream: TokenStream,
                        cattr: CharTermAttribute,
                        auxMap: Map[String,Int]): Map[String,Int] = {
    if (tokenStream.incrementToken()) {
      val tok = cattr.toString
      val occ = auxMap.getOrElse(tok, 0)
      getTokens(tokenStream, cattr, auxMap + ((tok,occ+1)))
    } else auxMap
  }

  /**
    * Checks if a given token is present in some of given fields present in the
    * similar docs index
    *
    * @param tok token whose presence will be checked
    * @param fields fields used to look for the token
    * @param searcher Lucene index used to look for similar documents
    * @param analyzer Lucene analyzer object
    * @return true if the given token is present in the fiels or false if not
    */
  private def hasToken(tok: String,
                       fields: Set[String],
                       searcher: IndexSearcher,
                       analyzer: Analyzer): Boolean = {
    val mqParser = new MultiFieldQueryParser(fields.toArray, analyzer)
//println(s"tok=$tok")
    if (tok.contains("(") || tok.contains(")") || tok.contains(":")) false
    else {
      val query =  mqParser.parse(tok)

      searcher.search(query, 1).totalHits.value > 0
    }
  }
}
