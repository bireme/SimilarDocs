package org.bireme.sd.service

object TopIndexTestService extends App {
  private def usage(): Unit = {
    Console.err.println("usage: TopIndexTestService\n" +
      "\n\t-sdIndexPath=<path>         : documents Lucene index path" +
      "\n\t-freqIndexPath=<path>       : decs frequency Lucene index path" +
      "\n\t-otherIndexPath=<path>      : other indexes directory path" +
      "\n\t-psId=<id>                  : personal service identification" +
      "\n\t\n--- and one of the following options: ---\n" +
      "\n\t-addProfile=<name>=<sentence> : add user profile" +
      "\n\t-deleteProfile=<name>         : delete user profile" +
      "\n\t-getSimDocs=<prof>,<prof>,... : get similar documents from profiles" +
      "\n\t--showProfiles                : show user profiles"
    )
    System.exit(1)
  }

  if (args.length != 5) usage()

  val parameters = args.foldLeft[Map[String,String]](Map()) {
    case (map,par) => {
      val split = par.split(" *= *", 2)
      if (split.length == 2) map + ((split(0).substring(1), split(1)))
      else map + ((split(0).substring(2), ""))
    }
  }
  val sdIndexPath = parameters("sdIndexPath")
  val freqIndexPath = parameters("freqIndexPath")
  val otherIndexPath = parameters("otherIndexPath")
  val psId = parameters("psId")
  val addProfile = parameters.get("addProfile")
  val delProfile = parameters.get("deleteProfile")
  val getSimDocs = parameters.get("getSimDocs")
  val showProfiles = parameters.contains("showProfiles")
  val docIndexPath = otherIndexPath +
                    (if (otherIndexPath.endsWith("/")) "" else "/") + "docIndex"
  val topIndexPath = otherIndexPath +
                    (if (otherIndexPath.endsWith("/")) "" else "/") + "topIndex"
  val topIndex = new TopIndex(sdIndexPath, docIndexPath, freqIndexPath,
                              topIndexPath, Set("ti", "ab"))
  addProfile match {
    case Some(profile) => {
      val split = profile.trim().split(" *\\= *", 2)
      if (split.length != 2) usage()
      topIndex.addProfile(psId, split(0), split(1))
    }
    case None => delProfile match {
      case Some(profId) => topIndex.deleteProfile(psId,profId)
      case None => getSimDocs match {
        case Some(fields) => println(topIndex.getSimDocsXml(psId, Set(),
                                      fields.trim().split(" *\\, *").toSet, 10))
        case None => if (showProfiles) println(topIndex.getProfilesXml(psId))
                     else usage()
      }
    }
  }
  topIndex.close()
}
