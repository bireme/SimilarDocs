
package org.bireme.sd.service

object TopIndexTestService extends App {
  private def usage(): Unit = {
    Console.err.println("usage: TopIndexTestService\n" +
      "\n\t-sdIndexPath=<path>         : documents Lucene index path" +
      "\n\t-freqIndexPath=<path>       : decs frequency Lucene index path" +
      "\n\t-otherIndexPath=<path>      : other indexes directory path" +
      "\n\t-psId=<id>                  : personal service identification" +
      "\n\t\n--- and one of the following options: ---\n" +
      "\n\t-addWords=<wrd1>,<wrd>,...  : add words to be used to look for sim docs" +
      "\n\t-getSimDocs=<fld>,<fld>,... : get fields from similar documents" +
      "\n\t--delPSRecord               : delete personal service record"
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
  val addWords = parameters.get("addWords")
  val getSimDocs = parameters.get("getSimDocs")
  val delPSRecord = parameters.get("delPSRecord")
  val docIndexPath = otherIndexPath +
                    (if (otherIndexPath.endsWith("/")) "" else "\\") + "docIndex"
  val topIndexPath = otherIndexPath +
                    (if (otherIndexPath.endsWith("/")) "" else "\\") + "topIndex"
  val topIndex = new TopIndex(sdIndexPath, docIndexPath, freqIndexPath,
                              topIndexPath, Set("ti", "ab"))
  addWords match {
    case Some(words) => topIndex.addWords(psId, words.trim().split(" *\\, *")
                                                                         .toSet)
    case None => getSimDocs match {
      case Some(fields) => topIndex.getSimDocsXml(psId, fields.trim())
      case None => delPSRecord match {
        case Some(_) => topIndex.delRecord(psId)
        case None => usage()
      }
    }
  }
  topIndex.close()
}
