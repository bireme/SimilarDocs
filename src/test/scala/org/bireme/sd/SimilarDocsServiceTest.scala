/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.sd

import java.net.{URI, URL}

import org.scalatest._
import org.scalatest.concurrent.Timeouts._
import org.scalatest.Matchers._
import org.scalatest.time.SpanSugar._

import scala.io._
import scala.util.matching.Regex

/** Application which uses ScalaTest to check each function from Similar Documents Service
*
* author: Heitor Barbieri
* date: 20170717
*/
class SimilarDocsServiceTest extends FlatSpec {

  /**
    * Load the content of a web page and check if there is a timeout
    *
    * @param url the address to the page to be downloaded
    * @return the page content
    */
  private def pageContent(url:String): String = {
    require(url != null)

    val url2 = new URL(url)
    val uri = new URI(url2.getProtocol, url2.getUserInfo, url2.getHost,
                     url2.getPort, url2.getPath, url2.getQuery, url2.getRef)
    val urlStr = uri.toASCIIString

    var content = ""
    failAfter(60.seconds) {
      val source = Source.fromURL(urlStr, "utf-8")
      content = source.getLines().mkString("\n")
      source.close()
    }

    content
  }

 /**
   * Count how many times a word appears in a String
   *
   * @param content the string into which the word will be searched
   * @param word the string to be searched
   */
  private def getOccurrences(content: String,
                             word: String): Int = {
    require (content != null)
    require (word != null)

    val split = content.trim.split("[\\s\\<\\>\\,\\.]+")
    val word2 = word.trim

    split.foldLeft[Int](0) {
      case(tot1,w) => if (word2.equals(w)) tot1 + 1 else tot1
    }
  }

  val service = "http://basalto01.bireme.br:8180/SDService"
  //val service = "http://serverofi5.bireme.br:8180/SDService"
  //val service = "http://localhost:8084"

  val id = "Téster!@paho.org"
  val profiles: Map[String, String] = Map(
    "é profile 0" -> "humano",
    "é profile 1" -> "zika dengue",
    "é profile 2" -> "febre amarela",
    "é profile 3" -> "mortalidade infantil",
    "O Fundo das Nações Unidas para a infância UNICEF mantém" +
      " uma ordenação dos países por taxa de mortalidade utilizando" +
      " um conceito chamado Under 5 mortality rate ou U5MR definido" +
      " pela OMS como a probabilidade de uma criança morrer até" +
      " aos cinco anos de idade por mil crianças nascidas vivas." -> (
    "Mortalidade infantil consiste na morte de crianças no " +
      " primeiro ano de vida e é a base para calcular a taxa de" +
      " mortalidade infantil, que consiste na mortalidade infantil" +
      " observada durante um ano, referida ao número de nascidos" +
      " vivos do mesmo período."))
  val regex: Regex = "<(ab|ti)(_[^>]+)?([^<]+)</$1>".r

  // === Check if the server is accessible ===
  "The Similar Documents Service page" should "be on" in {
    val title = "<title>([^<]+)</title>".r
    val url = s"$service"
    val content = pageContent(url)

    title.findFirstMatchIn(content) match {
      case Some(mat) => mat.group(1) should be ("Similar Documents Service")
      case None => fail
    }
  }

  // === Check if the Similar Documents applet is available ===
  "The Similar Documents Service servlet" should "be on" in {
    val url = s"$service/SDService"
    val content = pageContent(url)

    content should be ("<ERROR>missing 'psId' parameter</ERROR>")
  }

  // === Check the 'Add Profile' service ===
  profiles.foreach {
    p =>
      val url = s"$service/SDService?psId=$id&addProfile=${p._1}&sentence=${p._2}"
      val content = pageContent(url)

      s"The user '$id'" should s"insert user profile [${p._1}]" in {
        content should be ("<result>OK</result>")
      }
  }

  // === Check the 'Show Profiles' service ===
  s"The user '$id'" should "retrieve his/her profiles" in {
    val profs = "<name>([^<]+)</name>\\s+<content>([^<]+)</content>".r
    val url = s"$service/SDService?psId=$id&showProfiles="
    val content = pageContent(url)

    profs.findAllMatchIn(content).foreach {
      mat => profiles(mat.group(1)) should be (mat.group(2))
    }
  }

  // === Check the "Get Similar Documents" service (number of retrieved docs) ===
  val doc: Regex = "<document>".r
  profiles.foreach {
    p =>
      val url = s"$service/SDService?psId=$id&getSimDocs=${p._1}"
      val content = pageContent(url)

      s"The user '$id'" should s"retrieve at least 7 documents with profile [${p._1}]" in {
        doc.findAllMatchIn(content).size should be >= 7
      }
  }

  // === Check the "Get Similar Documents" service (quality of retrieved docs) ===
  profiles.foreach {
    prof =>
      val url = s"$service/SDService?psId=$id&getSimDocs=${prof._1}"
      val content = pageContent(url).toLowerCase
      val profWords: Set[String] = prof._2.toLowerCase.replaceAll("\\[\\-\\,\\:\\_]", " ").
        split("[\\s+\\,\\.]").filter(_.size > 3).toSet
      val common: Int = profWords.foldLeft[Int](0) {
        case (tot, word) => tot + getOccurrences(content, word)
      }
      s"The user '$id'" should
        s"retrieve documents with at least 7 match profile [$prof] words" in {
        common should be >= 7
      }
  }

  // === Check the 'Delete Profile' service ===
  profiles.foreach {
    p =>
      val profName = p._1
      val prof = s"<name>$profName</name>\\s+<content>[^<]+</content>".r
      val url = s"$service/SDService?psId=$id&deleteProfile=$profName"

      s"The user '$id'" should s"delete his/her profile [$profName]" in {
        prof.findFirstIn(pageContent(url)) should be (None)
      }
  }
}
