/*=========================================================================

    XDocumentServer © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/XDocumentServer/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.sd.others

import java.io._
import java.nio.charset.Charset
import java.nio.file.{Files, StandardOpenOption}
import java.text.SimpleDateFormat
import java.util.{Calendar, Date}

import scala.io.{BufferedSource, Source}
import scala.util.{Failure, Success, Try}

class FSDocServer(rootDir: File,
                  fileExtension: Option[String] = None) extends DocumentServer {
  val extension: String = fileExtension.map {
    ext =>
      val extt = ext.trim
      if (extt.isEmpty) ""
      else if (extt.head.equals('.')) extt
      else s".$extt"
  }.getOrElse("")

  // Create the root  directory if it does not exist
  Tools.createDirectory(rootDir)

  /**
    * List the ids of all pdf documents
    * @return a set having all pdf document ids
    */
  override def getDocuments: Set[String] = getDocuments(rootDir)

  /**
    * List the ids of all pdf documents
    * @param root father directory
    * @return a set having all pdf document ids
    */
  def getDocuments(root: File): Set[String] = {
    val esize = extension.length

    root.listFiles().foldLeft[Set[String]](Set()) {
      case (set, file) =>
        if (file.isDirectory) set ++ getDocuments(file)
        else {
          val fname: String = file.getName
          if (fname.endsWith(extension)) set + fname.substring(0, fname.length - esize)
          else set
        }
    }
  }

  /**
    * Retrieve a stored document
    *
    * @param id document identifier
    * @return the original document content (bytes) if it is found or 404 (not found) or 500 (internal server error)
    */
  override def getDocument(id: String): Either[Int, InputStream] = {
    val idT: String = id.trim
    val dir = new File(rootDir, idT)
    val file = new File(dir, s"$idT$extension")

    if (file.isFile || file.canRead) {
      Try(new FileInputStream(file)) match {
        case Success(fis) => Right(fis)
        case Failure(_) => Left(500)
      }
    } else Left(404)
  }

  /**
    * Store a new document
    * @param id document identifier
    * @param source the source of the document content
    * @param info metadata of the document
    * @return a http error code. 201(created), 409(conflict) is the id already exists or 500 (internal server error)
    */
  override def createDocument(id: String,
                              source: InputStream,
                              info: Option[Map[String, Set[String]]] = None): Int = {
    val idT: String = id.trim
    val dir = new File(rootDir, idT)
    val file = new File(dir, s"$idT$extension")
    val infoFile = new File(dir, s"$idT.info")
    val buffer = Array.ofDim[Byte](1024)

    if (file.exists() || infoFile.exists()) 409
    else {
      Try {
        if (!dir.exists()) dir.mkdir()
        if (writeDocument(source, file, buffer)) writeDocInfo(infoFile, info.getOrElse(createDocumentInfo(idT)))
        else 500
      } match {
        case Success(ret: Int) => ret
        case Failure(_) => 500
      }
    }
  }

  /**
    * Store a new document
    * @param id document identifier
    * @param url the location where the document is
    * @param info metadata of the document
    * @return a http error code. 201(created), 409(conflict) is the id already exists or 500(internal server error)
    */
  override def createDocument(id: String,
                              url: String,
                              info: Option[Map[String, Set[String]]]): Int = {
    val idT: String = id.trim
    val dir = new File(rootDir, idT)
    val file = new File(dir, s"$idT$extension")
    val infoFile = new File(dir, s"$idT.info")

    if (file.exists() || infoFile.exists()) 409
    else {
      Try {
        Tools.url2ByteArray(url) match {
          case Some(arr) =>
            if (arr.isEmpty) 500
            else {
              if (!dir.exists()) dir.mkdir()
              val fos = new FileOutputStream(file)
              fos.write(arr)
              fos.close()
              writeDocInfo(
                infoFile,
                info.getOrElse(createDocumentInfo(idT) + ("url" -> Set(url)))
              )
            }
          case None => 500
        }
      } match {
        case Success(ret) => ret
        case Failure(_) => 500
      }
    }
  }

  /**
    * Replace a stored document if there is some or create a new one otherwise
    * @param id document identifier
    * @param source the source of the document content
    * @param info metainfo of the document
    * @return a http error code. 201(created) if new , 200(ok) if replaced or 500 (internal server error)
    */
  override def replaceDocument(id: String,
                               source: InputStream,
                               info: Option[Map[String, Set[String]]] = None): Int = {
    deleteDocument(id) match {
      case 500 => 500
      case _ => createDocument(id, source, info)
    }
  }

  /**
    * Replace a stored document if there is some or create a new one otherwise
    * @param id document identifier
    * @param url the location where the document is
    * @param info metadata of the document
    * @return a http error code. 201(created) if new , 200(ok) if replaced or 500 (internal server error)
    */
  override def replaceDocument(id: String,
                               url: String,
                               info: Option[Map[String, Set[String]]]): Int = {
    deleteDocument(id) match {
      case 500 => 500
      case _ =>
        createDocument(id, url, info)
        200
    }
  }

  /**
    * Delete a stored document
    * @param id document identifier
    * @return a http error code. 200 (ok) or 404 (not found) or 500 (internal server error)
    */
  override def deleteDocument(id: String): Int = {
    val idT: String = id.trim
    val dir = new File(rootDir, idT)
    val file = new File(dir, s"$idT$extension")
    val info = new File(dir, s"$idT.info")

    if (dir.exists()) {
      val fstatus: Int = if (file.exists()) {
        if (file.delete()) 200 else 500
      } else 404
      val istatus = if (info.exists()) {
        if (info.delete()) 200 else 500
      } else 404
      if (Math.max(fstatus, istatus) == 500) 500
      else if (dir.delete()) 200 else 500
    } else 404
  }

  /**
    * Delete all stored documents
    * @return a http error code. 200 (ok) or or 500 (internal server error)
    */
  def deleteDocuments(): Int = {
    if (Tools.deleteDirectory(rootDir) && Tools.createDirectory(rootDir)) 200 else 500
  }

  /**
    * Retrieve metadata of a stored pdf document
    * @param id document identifier
    * @return the document metadata if found or 404 (not found) or 500 (internal server error)
    */
  override def getDocumentInfo(id: String): Either[Int, Map[String, Set[String]]] = {
    val idT: String = id.trim
    val dir = new File(rootDir, idT)
    val info = new File(dir, s"$idT.info")

    if (info.isFile || info.canRead) {
      Try {
        val src: BufferedSource = Source.fromFile(info, "utf-8")
        val map: Map[String, Set[String]] = src.getLines().foldLeft[Map[String, Set[String]]](Map()) {
          case (mp, line) =>
            val lineT = line.trim
            if (lineT.isEmpty) mp
            else {
              val split = lineT.split(" *= *", 2)
              if (split.size == 2) {
                val key: String = split(0)
                val set: Set[String] = mp.getOrElse(key, Set[String]())
                mp + (key -> (set + split(1)))
              } else mp
            }
        }
        src.close()
        map
      } match {
        case Success(map) => Right(map)
        case Failure(_) => Left(500)
      }
    } else Left(404)
  }

  /**
    * Create a metadata for the document
    * @param id document identifier (document id from FI Admin)
    * @param source source of the document content
    * @param info other metadata source
    * @return the document metadata
    */
  override def createDocumentInfo(id: String,
                                  source: Option[InputStream] = None,
                                  info: Option[Map[String, Set[String]]] = None): Map[String, Set[String]] = {
    val now: Date = Calendar.getInstance().getTime
    val dateFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss")
    val date: String = dateFormat.format(now)

    Map("id" -> Set(id), "date" -> Set(date)) ++ info.getOrElse(Map[String, Set[String]]())
  }

  private def writeDocInfo(infoFile: File,
                           info: Map[String, Set[String]]) : Int = {
    if (infoFile.isFile) 409
    else {
      Try {
        val writer: BufferedWriter = Files.newBufferedWriter(infoFile.toPath,
                                             Charset.forName("utf-8"), StandardOpenOption.CREATE_NEW)
        var first = true

        info.toList.foreach {
          case(k,v) =>
            v.foreach {
              value =>
                if (first) first = false
                else writer.newLine()
                writer.write(s"${k.trim()}=${value.replace("\n", " ").trim()}")
            }
        }
        writer.close()
      } match {
        case Success(_) => 201
        case Failure(_) => 500
      }
    }
  }

  private def writeDocument(is: InputStream,
                            file: File,
                            buffer: Array[Byte]): Boolean = {
    var continue = true

    Try {
      val os: OutputStream = new FileOutputStream(file)
      while (continue) {
        val read: Int = is.read(buffer)
//println(s"writeDocument read=$read")
        if (read >= 0) os.write(buffer, 0, read)
        else continue = false
      }
      os.close()
    } match {
      case Success(_) => true
      case Failure(_) => false
    }
  }
}
