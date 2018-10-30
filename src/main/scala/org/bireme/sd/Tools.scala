/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/


package org.bireme.sd

import java.io.File
import java.text.Normalizer
import java.text.Normalizer.Form
import java.util

import org.apache.lucene.index.{DirectoryReader, LeafReaderContext, TermsEnum}
import org.apache.lucene.store.FSDirectory

import scala.collection.immutable.TreeSet

/** Collection of helper functions
  *
  * author: Heitor Barbieri
  * date: 20170102
  *
*/
object Tools {

  /**
    * Converts all input charactes into a-z, 0-9, '_', '-' and spaces
    *
    * @param in input string to be converted
    * @return the converted string
    */
  def uniformString(in: String): String = {
    require (in != null)

    val s1 = Normalizer.normalize(in.trim().toLowerCase(), Form.NFD)
    val s2 = s1.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")

    //s2.replaceAll("\\W", " ")
    s2.replaceAll("[^\\w\\-]", " ")  // Hifen
  }

  /**
    * Converts all input charactes into a-z, 0-9, '_', '-' and spaces. Removes
    * adjacent whites and sort the words.
    *
    * @param in input string to be converted
    * @return the converted string
    */
  def strongUniformString(in: String): String = {
    require(in != null)

    val s1 = Normalizer.normalize(in.toLowerCase(), Form.NFD)
    val s2 = s1.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")

    TreeSet(s2.replaceAll("[^\\w\\-]", " ").trim().split(" +"): _*).
                                             filter(_.length >= 3).mkString(" ")
  }

  /**
    * Shows all index terms that are present in a specific fields
    *
    * @param indexName Lucene index path
    * @param fieldName Lucene document field that contains the terms to be
    * showed
    */
  def showTerms(indexName: String,
                fieldName: String): Unit = {
    val directory: FSDirectory = FSDirectory.open(new File(indexName).toPath)
    val ireader: DirectoryReader = DirectoryReader.open(directory)
    val leaves: util.List[LeafReaderContext] = ireader.leaves()

    if (!leaves.isEmpty) {
      val terms = leaves.get(0).reader().terms(fieldName)
      if (terms != null) {
        getNextTerm(terms.iterator()).foreach(x => println(s"[$x]"))
      }
    }
    ireader.close()
    directory.close()
  }

  /**
    * Creates a collection of fiels terms
    *
    * @param terms a enumerations of terms from a field
    * @return a stream of terms from a field
    */
  private def getNextTerm(terms: TermsEnum): Stream[String] = {
    if (terms == null) Stream.empty
    else {
      val next = terms.next()
      if (next == null) Stream.empty
      else next.utf8ToString() #:: getNextTerm(terms)
    }
  }
}

object ToolsApp extends App {
  private def usage(): Unit = {
    Console.err.println("usage: ToolsApp <indexName> <fieldName>")
    System.exit(1)
  }

  if (args.length != 2) usage()

  Tools.showTerms(args(0), args(1))
}
