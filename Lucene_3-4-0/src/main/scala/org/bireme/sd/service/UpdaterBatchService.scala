package org.bireme.sd.service

import java.io.File
import java.util.Calendar

import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.FSDirectory

object UpdaterBatchService extends App {
  private def usage(): Unit = {
    Console.err.println("usage: UpdateBatchService:\n" +
      "\n\t-sdIndexPath=<path>     : documents Lucene index path" +
      "\n\t-freqIndexPath=<path>   : decs frequency Lucene index path" +
      "\n\t-otherIndexPath=<path>  : other indexes directory path" +
      "\n\t-updAllDay=<day-number> : day to update all similar documents " +
                                       "index 1-sunday 7-saturday"
    )
    System.exit(1)
  }

  if (args.length != 4) usage()

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
  val updAllDay = parameters("updAllDay").toInt
  val docIndexPath = otherIndexPath +
                    (if (otherIndexPath.endsWith("/")) "" else "/") + "docIndex"
  val topIndexPath = otherIndexPath +
                    (if (otherIndexPath.endsWith("/")) "" else "/") + "topIndex"
  val updateAll = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == updAllDay)
  val docDirectory = FSDirectory.open(new File(docIndexPath))
  val docSearcher = new IndexSearcher(docDirectory)
  val freqDirectory = FSDirectory.open(new File(freqIndexPath))
  val freqSearcher = new IndexSearcher(freqDirectory)
  val idxFldName = Set("ti", "ab")
  val docIndex = new DocsIndex(sdIndexPath, docSearcher, freqSearcher, idxFldName)

  if (updateAll) docIndex.updateAllRecordDocs()
  else docIndex.updateNewRecordDocs()

  docIndex.close()
}
