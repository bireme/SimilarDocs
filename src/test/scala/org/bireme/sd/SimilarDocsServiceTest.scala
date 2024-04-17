/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.sd

import java.net.{URI, URL}
import java.util.{GregorianCalendar, TimeZone}

import org.bireme.sd.service.Conf
import org.scalatest.matchers.should.Matchers._
import org.scalatest.concurrent.TimeLimits._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.time.SpanSugar._

import scala.io._
import scala.util.matching.Regex

/** Application which uses ScalaTest to check each function from Similar Documents Service
*
* author: Heitor Barbieri
* date: 20170717
*/
class SimilarDocsServiceTest extends AnyFlatSpec {

  val todayCal: GregorianCalendar = new GregorianCalendar(TimeZone.getDefault)
  val endDayAgo: Int = Tools.timeToDays(todayCal.getTimeInMillis - Tools.getIahxModificationTime) + Conf.excludeDays
  val beginDayAgo: Int = endDayAgo + Conf.numDays

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

    val split = content.trim.split("[\\s<>,.]+")
    val word2 = word.trim

    split.foldLeft[Int](0) {
      case(tot1, wrd) => if (wrd.contains(word2)) tot1 + 1 else tot1
    }
  }

  //val service = "http://similardocs.bireme.org"
  //val service = "http://basalto01.bireme.br:8180/SDService"
  val service = "http://serverofi5.bireme.br:8180/SDService"
  //val service = "http://localhost:8084"

  val id = "Téster!@paho.org"
  val profiles: Map[String, String] = Map(
    "é profile 0" -> "humano",
    "é profile 1" -> "zika dengue",
    //"é profile 2  ~$%" -> "febre amarela",
    "é profile 2  ~$" -> "febre amarela",
    "é profile 3" -> "mortalidade infantil",
    "é profile 4" -> "saude brasil brazil",
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
  //val regex: Regex = "<(ab|ti)(_[^>]+)?([^<]+)</$1>".r

  // === Check if the server is accessible ===
  "The Similar Documents Service page" should "be on" in {
    val title = "<title>([^<]+)</title>".r
    val url = s"$service"
    val content = pageContent(url)

    title.findFirstMatchIn(content) match {
      case Some(mat) => mat.group(1) should be ("Similar Documents Service")
      case None => fail()
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

  // === Check the number of profiles ===
  s"The user '$id'" should s"retrieve all of his/her profiles [${profiles.size}]" in {
    val profs = "<name>([^<]+)</name>\\s*<content>([^<]+)</content>".r
    val url = s"$service/SDService?psId=$id&showProfiles="
    val content = pageContent(url)
    //profs.findAllMatchIn(content).foreach(x => println(s"profile=$x"))
    profs.findAllMatchIn(content).size should be (profiles.size)
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

  // === Check the "Get Similar Documents" service (number of retrieved docs) disregarding 'lastDays' parameter ===
  val doc: Regex = "<document score=".r
  profiles.foreach {
    prof =>
      val url = s"$service/SDService?adhocSimilarDocs=${prof._2}"
      val content = pageContent(url)
      s"The content of the profile '${prof._2}'" should s"have at least 10 similar documents (disregarding 'lastDays' parameter)" in {
        doc.findAllMatchIn(content).size should be >= 10
      }
  }

  // === Check the "Get Similar Documents" service (quality of retrieved docs) using 'lastDays' ===
  val profTotal: Map[String, Int] = profiles.foldLeft(Map[String, Int]()) {
    case (map, prof) =>
      val url = s"$service/SDService?adhocSimilarDocs=${prof._2}&lastDays=$beginDayAgo&sources=${Conf.sources.get.mkString(",")}"
      val content = pageContent(url)
      map + (prof._1 -> doc.findAllMatchIn(content).size)
  }

  val profTotalSum: Int = profTotal.values.sum
  if (profTotalSum == 0) {
    val profTotal: Map[String, Int] = profiles.foldLeft(Map[String, Int]()) {
      case (map, prof) =>
        val url = s"$service/SDService?adhocSimilarDocs=${prof._2}&lastDays=${2 * beginDayAgo}&sources=${Conf.sources.get.mkString(",")}"
        val content = pageContent(url)
        map + (prof._1 -> doc.findAllMatchIn(content).size)
    }
    s"The user '$id'" should "have at least 01 similar document" in {
      profTotal.values.sum should be > 0
    }
  }

  val doc1: Regex = "<document>".r
  profiles.foreach {
    prof =>
      val url: String = s"$service/SDService?psId=$id&getSimDocs=${prof._1}&considerDate="
      val content: String = pageContent(url).toLowerCase
      val found = doc1.findAllMatchIn(content).size
      val total = profTotal.getOrElse(prof._1, -1)

      s"The profile '${prof._1}'" should s"retrieve $total documents" in {
        found shouldBe total
      }
  }

  // === Check the quality of the similar documents ===
  val found: Iterable[String] = profTotal.filter(_._2 > 0).keys

  found.foreach {
    prof =>
      val url: String = s"$service/SDService?psId=$id&getSimDocs=$prof&considerDate="
      val content: String = pageContent(url).toLowerCase
      val profWords: Set[String] = profiles(prof).toLowerCase.replaceAll("\\[-,:_]", " ").
        split("[\\s+.]").filter(arr => arr.length > 3).toSet
      val common: Int = profWords.foldLeft[Int](0) {
        case (tot, word) =>
          val add = if (getOccurrences(content, word) > 0) 1 else 0
          tot + add
      }
      val size = if (profWords.size > 1) 2 else 1
      s"The user '$id' profile '$prof'" should s"retrieve documents with at least $size word(s) of the profile" in {
        common should be >= size
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

  val id_Renato = "renato.murasaki@bireme.org"
  val profiles_Renato: Map[String, String] = Map(
    "e-health" -> "digital e-health e-salud e-saude m-health saude",
    "Acupuntura" -> "acupuntura terapia",
    "enfermedades intestinales" -> "chron colitis enfermedades infecciosas intestinales ulcerativa")

  // === Check the number of profiles ===
  "Renato" should s"retrieve all of his profiles [${profiles_Renato.size}]" in {
    val profs = "<name>([^<]+)</name>\\s*<content>([^<]+)</content>".r
    val url = s"$service/SDService?psId=$id_Renato&showProfiles="
    val content = pageContent(url)
    profs.findAllMatchIn(content).size should be >= profiles_Renato.size
  }

  // === Check the "Get Similar Documents" service (number of retrieved docs) disregarding 'lastDays' parameter ===
  profiles_Renato.foreach {
    prof =>
      val url = s"$service/SDService?adhocSimilarDocs=${prof._2}"
      val content = pageContent(url)
      s"The content of the profile '${prof._2}'" should s"have at least 10 similar documents (disregarding 'lastDays' parameter)" in {
        doc.findAllMatchIn(content).size should be >= 8
      }
  }

  // === Check the "Get Similar Documents" service (quality of retrieved docs) using 'lastDays' parameter ===
  val profTotal2: Map[String, Int] = profiles_Renato.foldLeft(Map[String, Int]()) {
    case (map, prof) =>
      val url = s"$service/SDService?adhocSimilarDocs=${prof._2}&lastDays=$beginDayAgo&sources=${Conf.sources.get.mkString(",")}"
      val content = pageContent(url)
      map + (prof._1 -> doc.findAllMatchIn(content).size)
  }

  // if there are no documents then try with more 30 days
  if (profTotal2.values.sum == 0) {
    val profTotal2a: Map[String, Int] = profiles_Renato.foldLeft(Map[String, Int]()) {
      case (map, prof) =>
        val url = s"$service/SDService?adhocSimilarDocs=${prof._2}&lastDays=${beginDayAgo + 30}&sources=${Conf.sources.get.mkString(",")}"
        val content = pageContent(url)
        map + (prof._1 -> doc.findAllMatchIn(content).size)
    }
    "Renato" should "have at least 01 similar document" in {
      profTotal2a.values.sum should be > 0
    }
  } else {
    profiles_Renato.foreach {
      prof =>
        val url: String = s"$service/SDService?psId=$id_Renato&getSimDocs=${prof._1}&considerDate="
        val content: String = pageContent(url).toLowerCase
        val found = doc1.findAllMatchIn(content).size
        val total = profTotal2.getOrElse(prof._1, -1)

        s"Renato's profile '${prof._1}'" should s"retrieve $total documents" in {
          found shouldBe total
        }
    }

    // === Check the quality of the similar documents ===
    val found2: Iterable[String] = profTotal2.filter(_._2 > 0).keys

    found2.foreach {
      prof =>
        val url: String = s"$service/SDService?psId=$id_Renato&getSimDocs=$prof&considerDate="
        val content: String = pageContent(url).toLowerCase
        val profWords: Set[String] = profiles_Renato(prof).toLowerCase.replaceAll("\\[-,:_]", " ").
          split("[\\s+.]").filter(_.length > 3).toSet
        val common: Int = profWords.foldLeft[Int](0) {
          case (tot, word) =>
            val add = if (getOccurrences(content, word) > 0) 1 else 0
            tot + add
        }
        val size = if (profWords.size > 1) 2 else 1
        s"Renato's profile '$prof'" should s"retrieve documents with at least $size word(s) of the profile" in {
          common should be >= size
        }
    }
  }
}