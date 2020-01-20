/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.sd.others

import java.io.File
import java.util.Date

import org.apache.lucene.document.Document
import org.bireme.xds.XDocServer.{DocumentServer, FSDocServer}
import org.bireme.sd.SimDocsSearch
import org.bireme.sd.service.TopIndex

import scala.collection.immutable.TreeSet

/**
  * Create and/or update e-BlueInfo profiles in the TopIndex Lucene index. Also delete profiles
  * that are not used anymore.
  */
object UpdEBlueInfoProf extends App {
  private def usage(): Unit = {
    System.err.println("usage: UpdEBlueInfoProf <options>")
    System.err.println("options:")
    System.err.println("\t-pdfPath=<path> - path to the pdfs files")
    System.err.println("\t-decsPath=<path> - path/name to Lucene DeCS index")
    System.err.println("\t-simDocsPath=<path> - path/name to the Similar Documents Lucene index")
    System.err.println("\t-topIndexPath=<path> - path/name to the TopIndex Lucene index")
  }

  if (args.length != 4) usage()

  val startTime: Long = new Date().getTime
  val parameters: Map[String, String] = args.foldLeft[Map[String,String]](Map()) {
    case (map,par) =>
      val split = par.split(" *= *", 2)
      if (split.length == 1) map + ((split(0).substring(2), ""))
      else map + ((split(0).substring(1), split(1)))
  }

  val userName: String = "<<<e-BlueInfo>>>"
  val profName: String = "<<<id>>>>"

  val pdfPath: String = parameters("pdfPath")
  val decsPath: String = parameters("decsPath")
  val simDocsPath: String = parameters("simDocsPath")
  val topIndexPath: String = parameters("topIndexPath")

  updateProfiles(pdfPath, decsPath, simDocsPath, topIndexPath)

  def updateProfiles(pdfPath: String,
                     decsPath: String,
                     simDocsPath: String,
                     topIndexPath: String): Unit = {
    val docServer: DocumentServer = new FSDocServer(new File(pdfPath), Some(".pdf"))
    val simDocs: SimDocsSearch = new SimDocsSearch(simDocsPath, decsPath)
    val topIndex: TopIndex = new TopIndex(simDocs, topIndexPath)
    val docIds: Set[String] = docServer.getDocuments  // document ids from e-BlueInfo

    docIds.foreach {
      id =>   // e-BlueInfo document id
        docServer.getDocumentInfo(id) match {
          case Right(profMap: Map[String, Set[String]]) =>
            getDocInfoContent(profMap) match {
              case Some(infoContent: String) =>
                val profileName: String = profName.replace("id", id)
                getProfContent(topIndex, profileName) match {
                  case Some(profContent) =>
                    if (!infoContent.equals(profContent)) topIndex.addProfile(userName, profileName, infoContent)
                  case None => topIndex.addProfile(userName, profileName, infoContent)
                }
              case None => System.err.println(s"ERROR: failure while getting document metadata. Id=$id")
            }
          case Left(err) => System.err.println(s"ERROR: failure while getting document metadata. Id=$id. ErrCode=$err")
        }
    }
    topIndex.close()
  }

  /**
    *  Get a user profile content
    * @param topIndex index with user profiles
    * @param profName name of the desired profile
    * @return the profile content
    */
  private def getProfContent(topIndex: TopIndex,
                             profName: String): Option[String] = {
    topIndex.getDocuments(Map(topIndex.userFldName -> userName, topIndex.nameFldName -> profName)).flatMap {
      docList: List[Document] => // Profile associated to e-BlueInfo document id was found
        docList.headOption.flatMap(doc => Option(doc.get(topIndex.contentFldName)))
    }
  }

  /**
    *  Get a document info content
    * @param info document meta information
    * @return a string having the union of strings of the fields title, descriptors, abstract from e-BlueInfo document info
    */
  private def getDocInfoContent(info: Map[String, Set[String]]): Option[String] = {
    val title: Option[TreeSet[String]] = info.get("ti").map {
      _.foldLeft(TreeSet[String]()) {
        case (tset, tit) => tset + tit
      }
    }
    val descriptors: Option[TreeSet[String]] = info.get("mh").map {
      _.foldLeft(title.getOrElse(TreeSet[String]())) {
        case (dset, descr) => dset + descr
      }
    }
    val abstracts: Option[TreeSet[String]] = info.get("ab").map {
      _.foldLeft(descriptors.getOrElse(TreeSet[String]())) {
        case (aset, abstr) => aset + abstr
      }
    }
    abstracts.map(_.mkString(" "))
  }
}
