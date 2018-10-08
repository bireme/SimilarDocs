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

package org.bireme.sd.service

import org.bireme.sd.SimDocsSearch

/**
  * Application to test the same services offered by the associated web services
  *
  * author: Heitor Barbieri
  * date: 20170110
  */
object TopIndexTestService extends App {
  private def usage(): Unit = {
    Console.err.println("usage: TopIndexTestService\n" +
      "\n\t-sdIndexPath=<path>         : documents Lucene index path" +
      "\n\t-topIndexPath=<path>        : top Lucene index path" +
      "\n\t-decsIndexPath=<path>       : decs Lucene index path" +
      "\n\t-psId=<id>                  : personal service identification" +
      "\n\t\n--- and one of the following options: ---\n" +
      "\n\t-addProfile=<name>=<sentence> : add user profile" +
      "\n\t-deleteProfile=<name>         : delete user profile" +
      "\n\t-getSimDocs=<prof>,<prof>,... : get similar documents from profiles" +
      "\n\t--cleanSimDocs                : erase all similar documents fiels (sd_id) from profiles" +
      "\n\t--showProfiles                : show user profiles"
    )
    System.exit(1)
  }

  if (args.length != 5) usage()

  val parameters = args.foldLeft[Map[String,String]](Map()) {
    case (map,par) =>
      val split = par.split(" *= *", 2)
      if (split.length == 2) map + ((split(0).substring(1), split(1)))
      else map + ((split(0).substring(2), ""))
  }
  val sdIndexPath = parameters("sdIndexPath")
  val topIndexPath = parameters("topIndexPath")
  val decsIndexPath = parameters("decsIndexPath")
  val psId = parameters("psId")
  val addProfile = parameters.get("addProfile")
  val delProfile = parameters.get("deleteProfile")
  val getSimDocs = parameters.get("getSimDocs")
  val cleanSimDocs = parameters.contains("cleanSimDocs")
  val showProfiles = parameters.contains("showProfiles")
  val simDocs = new SimDocsSearch(sdIndexPath, decsIndexPath)
  val topIndex = new TopIndex(simDocs, topIndexPath, Conf.idxFldNames)
  addProfile match {
    case Some(profile) =>
      val split = profile.trim().split(" *\\= *", 2)
      if (split.length != 2) usage()
      topIndex.addProfile(psId, split(0), split(1))
    case None => delProfile match {
      case Some(profId) => topIndex.deleteProfile(psId,profId)
      case None => getSimDocs match {
        case Some(fields) => println(topIndex.getSimDocsXml(psId,
                        fields.trim().split(" *\\, *").toSet, Set(), 10))
        case None => if (showProfiles) println(topIndex.getProfilesXml(psId))
                     else if (cleanSimDocs) topIndex.resetAllTimes()
                     else usage()
      }
    }
  }
  topIndex.close()
  simDocs.close()
}
