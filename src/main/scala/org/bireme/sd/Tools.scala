/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/


package org.bireme.sd

import java.io.File
import java.text.{Normalizer, SimpleDateFormat}
import java.text.Normalizer.Form
import java.util
import java.util.Date

import org.apache.http.client.methods.{CloseableHttpResponse, HttpGet}
import org.apache.http.impl.client.{CloseableHttpClient, HttpClients}
import org.apache.http.util.EntityUtils
import org.apache.lucene.index.{DirectoryReader, LeafReaderContext, TermsEnum}
import org.apache.lucene.store.FSDirectory

import scala.collection.mutable
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

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
    require(in != null)

    val s1: String = Normalizer.normalize(in.trim().toLowerCase(), Form.NFD)
    val s2: String = s1.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")

    //s2.replaceAll("\\W", " ")
    s2.replaceAll("[^\\w\\-]", " ") // Hifen
  }

  /**
    * Converts all input charactes into a-z, 0-9, '_', '-' and spaces. Removes
    * adjacent whites and optionally sort the words.
    *
    * @param in input string to be converted
    * @return the converted string
    */
  def strongUniformString(in: String,
                          sort: Boolean = false): String = {
    require(in != null)

    val s1: String = Normalizer.normalize(in.toLowerCase(), Form.NFD)
    val s2: String = s1.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
    val split: Array[String] = s2.replaceAll("[^\\w\\-]", " ").trim().split(" +")
      .filter(_.length >= 3)
    val set: mutable.Set[String] = if (sort) mutable.SortedSet[String]()
    else mutable.LinkedHashSet[String]()
    val seq: Seq[String] = {
      split.foreach(set.add)
      set.toSeq
    }
    seq.mkString(" ")
  }

  /**
    * Shows all index terms that are present in a specific fields
    *
    * @param indexName Lucene index path
    * @param fieldName Lucene document field that contains the terms to be
    *                  showed
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
  //private def getNextTerm(terms: TermsEnum): Stream[String] = {
  private def getNextTerm(terms: TermsEnum): LazyList[String] = {
    if (terms == null) LazyList.empty
    else {
      val next = terms.next()
      if (next == null) LazyList.empty
      else next.utf8ToString() #:: getNextTerm(terms)
    }
  }

  /** *
    * Calculate the last date that is secure to include documents, because after that the document could not be
    * processed by IAHx.
    *
    * @return the iahx index document modification date in miliseconds
    */
  def getIahxModificationTime: Long = {
    val iahx: String = "http://basalto02.bireme.br:8986/solr5/admin/cores?action=STATUS"
    val regex: Regex = "(?<=lastModified\">)([^<]+)".r
    val df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'") // 2019-10-06T19:09:52.79Z

    Try {
      val httpclient: CloseableHttpClient = HttpClients.createDefault()
      val httpget: HttpGet = new HttpGet(iahx)
      val response: CloseableHttpResponse = httpclient.execute(httpget)

      response.getStatusLine.getStatusCode match {
        case 200 => Option(EntityUtils.toString(response.getEntity)).flatMap {
          line =>
            regex.findFirstMatchIn(line).map {
              mat => df.parse(mat.group(1)).getTime
            }
        }
        case _ => None
      }
    } match {
      case Success(value) => value.get
      case Failure(_) => new Date().getTime
    }
  }

  /**
    * Convert days into miliseconds
    *
    * @param days number of days to be converted
    * @return number of converted miliseconds
    */
  def daysToTime(days: Int): Long = (days * 24 * 60 * 60 * 1000).toLong

  /**
    * Convert time in milisecongs into days
    * @param time number of miliseconds since 1970
    * @return the number of converted days
    */
  def timeToDays(time: Long): Int = {
    val quo = 24 * 60 * 60 * 1000
    val div = (time / quo).toInt
    val mod = (time % quo).toInt

    if (mod == 0) div else (div + 1)
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