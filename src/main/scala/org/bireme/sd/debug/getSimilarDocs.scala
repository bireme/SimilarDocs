package org.bireme.sd.debug

import org.bireme.sd.{SimDocsSearch, Tools}
import org.bireme.sd.service.{Conf, TopIndex}

import java.text.SimpleDateFormat
import java.util.Date

object getSimilarDocs extends App {
  val sdIndexPath: String = "/home/javaapps/sbt-projects/SimilarDocs/indexes/sdIndex"
  val decsIndexPath: String = "/home/javaapps/sbt-projects/SimilarDocs/indexes/decsIndex"
  val decsPath: String = "/home/javaapps/sbt-projects/SimilarDocs/decs/decs"
  val topIndexPath: String = "/home/javaapps/sbt-projects/SimilarDocs/indexes/topIndex"
  val psId: String = "wilsonsmoura@gmail.com"
  val profiles: Set[String] = Set("Febre Amarela")

  println(getSimilar(sdIndexPath, decsPath, decsIndexPath, topIndexPath, psId, profiles,
    resetAllTimes=true, considerDate=true))

  private def getSimilar(sdIndexPath: String,
                         decsIndexPath: String,
                         decsPath: String,
                         topIndexPath: String,
                         psId: String,
                         profiles: Set[String],
                         outFields: Set[String] = Set(),
                         considerDate: Boolean = false,
                         resetAllTimes: Boolean = false): String = {
    val simSearch = new SimDocsSearch(sdIndexPath, decsPath, decsIndexPath)

    val topIndex = new TopIndex(simSearch, topIndexPath)
    if (resetAllTimes) topIndex.resetAllTimes()

    val beginDate: Option[Long] = if (considerDate) {
      val df = new SimpleDateFormat("yyyy-MM-dd")
      val modTime = Tools.getIahxModificationTime
      val excDays = Conf.excludeDays + Conf.numDays
      val excTime = Tools.daysToTime(excDays)
      val begTime =  modTime - excTime
      val date = new Date(begTime)
      println(s"modTime=$modTime excDays=$excDays excTime=$excTime begTime=$begTime beginDate=${df.format(date)}")
      Some(Tools.getIahxModificationTime - Tools.daysToTime(Conf.excludeDays + Conf.numDays))
    } else None

    val sim: String =
      topIndex.getSimDocsXml(psId, profiles, outFields, Conf.maxDocs, beginDate, Conf.sources, Conf.instances)

    simSearch.close()
    topIndex.close()

    sim
  }
}
