/*=========================================================================

    XDocumentServer © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/XDocumentServer/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.sd.others

import java.io.{ByteArrayOutputStream, File, InputStream}
import java.net._
import java.security.cert.X509Certificate
import java.text.Normalizer
import java.text.Normalizer.Form

import javax.net.ssl.{HostnameVerifier, HttpsURLConnection, SSLContext, SSLSession, TrustManager, X509TrustManager}

import scala.util.{Failure, Success, Try}

object Tools {
  def uniformString(in: String): String = {
    require (in != null)

    val s1 = Normalizer.normalize(in.trim().toLowerCase(), Form.NFD)

    s1.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
  }

  /**
    * Convert an input stream into a byte array
    * @param is the input stream to be converted
    * @return an output byte array
    */
  private def inputStream2Array(is: InputStream): Option[Array[Byte]] = {
    Try {
      val bos = new ByteArrayOutputStream()
      val buffer = Array.ofDim[Byte](2048)
      var continue = true

      while (continue) {
        val read = is.read(buffer)
  //println(s"inputStream2Array read=$read")
        if (read >= 0)
          bos.write(buffer, 0, read)
        else continue = false
      }

      bos.close()
      Some(bos.toByteArray)
    } match {
      case Success(array) => array
      case Failure(_) => None
    }
  }

  /**
    * Convert an url into a input stream
    * @param urls input url string
    * @return the output input stream
    */
  private def url2InputStream(urls: String): Option[InputStream] = {
    Try {
      val url: URL = new URL(urls)
      url.getProtocol match {
        case "http" | "https" => getHttpInputStream(urls)
        case "ftp" => url.openStream()
        case _ => throw new IllegalArgumentException("protocol")
      }
    } match {
      case Success(is) => Some(is)
      case Failure(ex) =>
        println(s"--- Downloading error. url:$urls msg:${ex.toString}")
        None
    }
  }

  /**
    * Convert an url into a string
    * @param urls input url string
    * @return the output string
    */
  def url2String(urls: String): Option[String] = {
    url2InputStream(urls).map {
      is => scala.io.Source.fromInputStream(is).mkString
    }
  }

  @scala.annotation.tailrec
  private def getHttpInputStream(urls: String): InputStream = {
    val timeout = 4 * 60 * 1000
    val url: URL = new URL(urlEncode(urls))
    val conn: HttpURLConnection = url.openConnection().asInstanceOf[HttpURLConnection]

    conn.setConnectTimeout(timeout)
    conn.setReadTimeout(timeout)
    conn.setInstanceFollowRedirects(false)   // Make the logic below easier to detect redirections
    conn.setRequestProperty("User-Agent", "Mozilla/5.0")

    conn.getResponseCode match {
      case HttpURLConnection.HTTP_MOVED_PERM | HttpURLConnection.HTTP_MOVED_TEMP =>
        val location: String = conn.getHeaderField("Location")
        val location2: String = URLDecoder.decode(location, "UTF-8")
        val original: String = URLDecoder.decode(urls, "UTF-8")
        if (location2.equals(original)) {
          throw new Exception("invalid redirection")
        }
        val next: URL = new URL(url, location2)  // Deal with relative URLs
        val url2: String = next.toExternalForm
        getHttpInputStream(url2)
      case _ => conn.getInputStream
    }
  }

  /**
    * Convert an url into a byte array
    * @param urls the input url string
    * @return the output byte array
    */
  def url2ByteArray(urls: String): Option[Array[Byte]] = {
    url2InputStream(urls).flatMap {
      is =>
        val arr: Option[Array[Byte]] = inputStream2Array(is)
        is.close()
        arr
    }
    /*val timeout = 4 * 60 * 1000

    Try(Http(url.toString).timeout(timeout, timeout).asBytes) match {
      case Success(response) =>
        val arr: Array[Byte] = response.body
        if (response.is2xx) Some(arr)
        else None
      case Failure(_) => None
    }*/
  }

  /**
    * Create a new directory
    * @param directoryToBeCreated the path to the directory
    * @return true if the file was created or false otherwise
    */
  def createDirectory(directoryToBeCreated: File): Boolean =
    (directoryToBeCreated == null) || directoryToBeCreated.mkdirs()

  /**
    * Delete a directory
    * @param directoryToBeDeleted the path to the directory
    * @return true if the file was deleted or false otherwise
    */
  def deleteDirectory(directoryToBeDeleted: File): Boolean = {
    val allContents = directoryToBeDeleted.listFiles
    if (allContents != null) allContents.foreach(file => deleteDirectory(file))
    directoryToBeDeleted.delete
  }

  def map2String(map: Map[String,Set[String]]): String = {
    map.foldLeft("") {
      case (str, kv) => kv._2.foldLeft(str) {
        case (str2, value) => str2 + (if (str.isEmpty) "" else "ºº") + s"${kv._1.trim}=${value.trim}"
      }
    }
  }

  def string2Map(str: String): Map[String, Set[String]] = {
    str.split("ºº").foldLeft(Map[String, Set[String]]()) {
      case (mp, x) =>
        val split: Array[String] = x.split("=")
        val key: String = split(0)
        val set: Set[String] = mp.getOrElse(key, Set[String]())
        mp + (key -> (set + split(1)))
    }
  }

  /**
    * Encoding a url. See https://stackoverflow.com/questions/724043/http-url-address-encoding-in-java
    * @param surl input url string to encode
    * @return the encoded url
    */
  def urlEncode0(surl: String): String = {
    val url = new URL(URLDecoder.decode(surl, "utf-8"))     // To avoid double encoding
    val uri = new URI(url.getProtocol, url.getUserInfo, url.getHost,
                      url.getPort, url.getPath, url.getQuery, url.getRef)
    //url.getPort, URLEncoder.encode(url.getPath, "utf-8"), url.getQuery, url.getRef)
     uri.toURL.toString  // Do not treat # in the URLpath
  }

  private def urlEncode(urls: String,
                        encod: String = "utf-8"): String = {
    val url = new URL(URLDecoder.decode(urls, encod))
    val protocol = url.getProtocol
    val authority = url.getAuthority
    val path = encodePath(url.getPath, encod)
    val query = url.getQuery
    val query2 = if (query == null) "" else s"?${encodeQuery(query, encod)}"
    val fragment = url.getRef
    val fragment2 = if (fragment == null) "" else s"#${encodeFragment(fragment, encod)}"

    protocol + "://" + authority + path + query2 + fragment2
  }

  private def encodePath(path: String,
                         encod: String): String = {
    val split = path.split("/", 100)

    split.map(URLEncoder.encode(_, encod)).map(_.replace("+", "%20")).mkString("/")
  }

  private def encodeQuery(query: String,
                          encod: String): String = { // xxx=yyy&www=zzz
    def encodeQueryPart(part: String): String = {  // xxx=yyy
      val split = part.split("=")
      if (split.length != 2) throw new IllegalArgumentException(part)

      split.map(URLEncoder.encode(_, encod)).mkString("=")
    }

    val amp = query.split("&")

    amp.map(encodeQueryPart).mkString("&")
  }

  private def encodeFragment(fragment: String,
                             encod: String): String = {
    URLEncoder.encode(fragment, encod).replace("+", "%20")
  }

  /**
  * Given two info structures (map), combine then into a only one
    * @param info1 - first info structure
    * @param info2 - second info structure
    * @return the merged info structure
    */
  def mergeInfo(info1: Map[String, Set[String]],
                info2: Map[String, Set[String]]): Map[String, Set[String]] = {
    info1.foldLeft(info2) {
      case (inf2, kv1:(String,Set[String])) => inf2.get(kv1._1) match {
        case Some(seq2: Set[String]) => inf2 + (kv1._1 -> (kv1._2 ++ seq2))
        case None => inf2 + (kv1._1 -> kv1._2)
      }
    }
  }

  // See: https://nakov.com/blog/2009/07/16/disable-certificate-validation-in-java-ssl-connections/
  def disableCertificateValidation(): Unit = {

    // Create a trust manager that does not validate certificate chains
    val trustAllCerts = Array[TrustManager] {
      new X509TrustManager() {
        def getAcceptedIssuers: Array[java.security.cert.X509Certificate] = null
        def checkClientTrusted(certs: Array[X509Certificate],
                               authType: String): Unit = {}
        def checkServerTrusted(certs: Array[X509Certificate],
                               authType: String): Unit = {}
      }
    }

    // Install the all-trusting trust manager
    val sc: SSLContext = SSLContext.getInstance("SSL")
    sc.init(null, trustAllCerts, new java.security.SecureRandom())
    HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory)

    // Create all-trusting host name verifier
    val allHostsValid: HostnameVerifier = (_: String, _: SSLSession) => true

    // Install the all-trusting host verifier
    HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid)
  }
}
