package org.bireme.sd.service

import java.io.File
import java.util.Calendar

import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.FSDirectory

object UpdaterBatchService extends App {
  private def usage(): Unit = {
    Console.err.println("usage: UpdateBatchService:\n" +
      "\n\t-sdIndexPath=<path>     : documents Lucene index path" +
      "\n\t-docIndexPath=<path>    : doc indexes directory path" +
      "\n\t-freqIndexPath=<path>   : decs frequency Lucene index path" +
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
  val docIndexPath = parameters("docIndexPath")

  val sdIndexPath = parameters("sdIndexPath")
  val sdDirectory = FSDirectory.open(new File(sdIndexPath))
  val sdSearcher = new IndexSearcher(sdDirectory)

  val freqIndexPath = parameters("freqIndexPath")
  val freqDirectory = FSDirectory.open(new File(freqIndexPath))
  val freqSearcher = new IndexSearcher(freqDirectory)

  val updAllDay = parameters("updAllDay").toInt
  val updateAll = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == updAllDay)
  val idxFldName = Set("ti", "ab")

  val docIndex = new DocsIndex(docIndexPath, sdSearcher, freqSearcher, idxFldName)
  if (updateAll) docIndex.updateAllRecordDocs()
  else docIndex.updateNewRecordDocs()

  docIndex.close()
}
