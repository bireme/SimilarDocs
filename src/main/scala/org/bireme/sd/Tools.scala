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
import org.apache.lucene.index.TermsEnum
import org.apache.lucene.store.FSDirectory

/** Collection of helper functions
  *
  * @author: Heitor Barbieri
  * date: 20170102
  *
*/
object Tools extends App {
  private def usage(): Unit = {
    Console.err.println("usage: Tools <indexName> <fieldName>")
    System.exit(1)
  }

  if (args.length != 2) usage();

  showTerms(args(0), args(1))

  /**
    * Shows all index terms that are present in a specific fields
    *
    * @param indexName Lucene index path
    * @param fieldName Lucene document field that contains the terms to be
    * showed
    */
  def showTerms(indexName: String,
                fieldName: String): Unit = {
    val directory = FSDirectory.open(new File(indexName).toPath())
    val ireader = DirectoryReader.open(directory);
    val leaves = ireader.leaves()

    if (!leaves.isEmpty()) {
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
