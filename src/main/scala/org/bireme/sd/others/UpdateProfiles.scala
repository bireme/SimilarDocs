/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.sd.others

import org.apache.lucene.document.Document
import org.bireme.sd.SimDocsSearch
import org.bireme.sd.service.TopIndex

import scala.collection.immutable.TreeSet
import scala.io.{BufferedSource, Source}

object UpdateProfiles extends App {
  type Info = Map[String, Set[String]]

  private def usage(): Unit = {
    System.err.println("usage: UpdateProfiles <options>")
    System.err.println("\nOptions:")
    System.err.println("\t-userName=<name> - SimilarDocs profile owner")
    System.err.println("\t-infoPath=<path> - path to info json file")
    System.err.println("\t-decsIndexPath=<path> - path to the Lucene DeCS index")
    System.err.println("\t-topIndexPath=<path> - path to the Lucene topIndex index")
    System.err.println("\t-sdIndexPath=<path> - path to the Lucene sdIndex index")
    System.exit(1)
  }

  if (args.length != 5) usage()

  val parameters = args.foldLeft[Map[String,String]](Map()) {
    case (map,par) =>
      val split = par.split(" *= *", 2)
      if (split.length == 2) map + ((split(0).substring(1), split(1)))
      else {usage(); map}
  }
  val userName = parameters("userName")
  val infoPath = parameters("infoPath")
  val decsIndexPath = parameters("decsIndexPath")
  val topIndexPath = parameters("topIndexPath")
  val sdIndexPath = parameters("sdIndexPath")

  val buff: BufferedSource = Source.fromFile(infoPath, "utf-8")
  val json: String = buff.getLines().mkString("\n")
  buff.close()

  InfoImpExp.json2Map(json) match {
    case Some(infos) =>
      val simSearch: SimDocsSearch = new SimDocsSearch(sdIndexPath, decsIndexPath)
      val topIndex: TopIndex = new TopIndex(simSearch, topIndexPath)

      updProfiles(userName, infos, topIndex)
    case None => System.err.println("Error during the import of info file")
  }

  /**
    * Create and/or update SimilarDocs profiles in the TopIndex Lucene index. Also delete profiles
    * that are not used anymore.
    *
    * @param userName topIndex user name
    * @param infos a map of all info to be include in the user's profile
    * @param topIndex path to Lucene profile index
    */
  def updProfiles(userName: String,
                  infos: Map[String, Info],
                  topIndex: TopIndex): Unit = {
    val usrName = userName.trim

    infos foreach {
      case (id, info) =>
        val profileName = s"~~~$id~~~"
        println(s"+++id=$id")
        getDocInfoContent(info) match {
          case Some(infoContent: String) =>
            val infoContentT = infoContent.trim
            if (infoContentT.nonEmpty) {
              getProfContent(usrName, topIndex, profileName) match {
                case Some(profContent) =>
                  if (!infoContentT.trim.equals(profContent)) topIndex.addProfile(usrName, profileName, infoContentT)
                case None => topIndex.addProfile(usrName, profileName, infoContentT)
              }
            }
          case None => System.err.println(s"ERROR: failure while getting document metadata. Id=$id")
        }
    }
    topIndex.close()
  }

  /**
    *  Get a user profile content
    * @param userName topIndex user name
    * @param topIndex index with user profiles
    * @param profName name of the desired profile
    * @return the profile content
    */
  private def getProfContent(userName: String,
                             topIndex: TopIndex,
                             profName: String): Option[String] = {
    topIndex.getDocuments(Map(topIndex.userFldName -> userName, topIndex.nameFldName -> profName)).flatMap {
      docList: List[Document] => // Profile associated to the document id was found
        docList.headOption.flatMap(doc => Option(doc.get(topIndex.contentFldName)))
    }
  }

  /**
    *  Get a document info content
    * @param info document meta information
    * @return a string having the union of strings of the fields title, descriptors, abstract from the document info
    */
  private def getDocInfoContent(info: Info): Option[String] = {
    val title: Set[String] = info.getOrElse("ti", Set[String]())
    val descriptors: Set[String] = info.getOrElse("mh", Set[String]())
    val abstracts: Set[String] = info.getOrElse("ab", Set[String]())
    val all: TreeSet[String] = TreeSet[String]() ++ title ++ descriptors ++ abstracts

    if (all.isEmpty) None
    else Some(all.mkString(" "))
  }
}
