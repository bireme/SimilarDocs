
package org.bireme.sd.service

/*
import com.mongodb.casbah.Imports._

import java.io.{File,IOException}

import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.FSDirectory

import org.bireme.sd.{SDAnalyzer, SDTokenizer, SimilarDocs}
import org.bireme.sd.SimilarDocs

import scala.collection.immutable.TreeSet
import scala.util.{Try, Success, Failure}

class DocsIndex_Mongo(mongoDB: MongoDB,
                      index: String,
                      freqIndex: String,
                      idxFldName: Set[String]) {
  val coll = mongoDB("docs")
  val analyzer = new SDAnalyzer(SDTokenizer.defValidTokenChars, true)
  val directory = FSDirectory.open(new File(index))
  val isearcher = new IndexSearcher(directory)
  val simDocs = new SimilarDocs()

  def newRecord(id: TreeSet[String]): Try[String] = {
    val _id = MongoDBList(id.map(_.toLowerCase()).toSeq:_*)
    val query = MongoDBObject("_id" -> _id)
    val document: MongoDBObject = coll.findOneByID(_id) match {
      case Some(doc:BasicDBObject) => new MongoDBObject(doc.append("is_new", false))
      case _ => MongoDBObject("_id" -> _id, "is_new" -> true)
    }
    val cmdResult = coll.update(query, document, upsert = true).getLastError()
    if (cmdResult.ok()) Success("ok")
    else Failure(new IOException(cmdResult.gerErrorMessage()))
  }

  def deleteRecord(id: TreeSet[String]): Try[String] = {
    val _id = MongoDBList(id.map(_.toLowerCase()).toSeq:_*)
    val cmdResult = coll.remove(MongoDBObject("_id" -> _id))
    if (cmdResult.ok()) Success("ok")
    else Failure(new IOException(cmdResult.getErrorMessage()))
  }

  def getDocIds(id: TreeSet[String]): Try[Set[Int]] = {
    val _id = MongoDBList(id.map(_.toLowerCase()).toSeq:_*)
    coll.findOneByID(_id) match {
      case Some(doc:BasicDBObject) => Try {
        val document = new MongoDBObject(doc)
        val list = new MongoDBList(document("doc_id").asInstanceOf[BasicDBList])
        list.foldLeft[Set[Int]](Set()) {
          case (set, i) => set + i.asInstanceOf[Int]
        }
      }
      case _ => Failure(new IllegalArgumentException(s"invalid id:${_id}"))
    }
  }

  def updateRecordDocs(id: TreeSet[String],
                       minMatch: Int = 3,
                       maxDocs: Int = 10): Try[String] = {
    val _id = MongoDBList(id.map(_.toLowerCase()).toSeq:_*)
    val query = MongoDBObject("_id" -> _id)

    coll.findOneByID(_id) match {
      case Some(doc:BasicDBObject) =>
        val mdoc = new MongoDBObject(doc.append("is_new", false))
        val (_,ids) = simDocs.search(id, analyzer, isearcher, idxFldName,
                                                   freqIndex, minMatch, maxDocs)
        val cmdResult = coll.update(query, mdoc +
                                        (("doc_id", MongoDBList(ids.toSeq:_*))))
        if (cmdResult.ok()) Success("ok")
        else Failure(cmdResult.getErrorMessage())
      case _ => Failure(new IllegalArgumentException(s"invalid id:$id"))
    }
  }
}
*/
