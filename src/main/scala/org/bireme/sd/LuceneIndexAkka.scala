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

package org.bireme.sd

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem}
import akka.actor.{PoisonPill, Props, Terminated}
import akka.routing.{ ActorRefRoutee, Broadcast, RoundRobinRoutingLogic, Router}

import bruma.master._

import java.io.File
import java.util.regex.Pattern

import org.apache.lucene.document.{Document,Field,StoredField,TextField}
import org.apache.lucene.index.{IndexWriter,IndexWriterConfig}
import org.apache.lucene.store.FSDirectory

import scala.collection.JavaConverters._
import scala.language.postfixOps
import scala.util.control.NonFatal
import scala.concurrent.Await
import scala.concurrent.duration._
//import scala.concurrent.ExecutionContext.Implicits.global

class LuceneIndexMain(indexPath: String,
                      xmlDir: String,
                      xmlFileFilter: String,
                      fldIdxNames: Set[String],
                      fldStrdNames: Set[String],
                      decsDir: String,
                      encoding: String) extends Actor with ActorLogging {
  val idxWorkers = 10 // Number of actors to run concurrently

  val analyzer = new NGramAnalyzer(NGSize.ngram_size)
  val directory = FSDirectory.open(new File(indexPath).toPath())
  val config = new IndexWriterConfig(analyzer)
  config.setOpenMode(IndexWriterConfig.OpenMode.CREATE)
  val indexWriter = new IndexWriter(directory, config)
  val decsMap = if (decsDir.isEmpty()) Map[Int,Set[String]]()
                else decx2Map(decsDir)
  val routerIdx = {
    val routees = Vector.fill(idxWorkers) {
      val r = context.actorOf(Props(classOf[LuceneIndexActor], indexWriter,
                                            fldIdxNames, fldStrdNames, decsMap))
      context watch r
      ActorRefRoutee(r)
    }
    Router(RoundRobinRoutingLogic(), routees)
    //Router(SmallestMailboxRoutingLogic(), routees)
  }
  val matcher = Pattern.compile(xmlFileFilter).matcher("")
  var activeIdx  = idxWorkers


  (new File(xmlDir)).listFiles().sorted.foreach {
    file =>
      if (file.isFile()) {
        matcher.reset(file.getName())
        if (matcher.matches) indexFile(file.getPath(), encoding)
      }
  }

  // finishing Actors
  routerIdx.route(Broadcast(PoisonPill), self)

  override def postStop(): Unit = {
    log.info("Optimizing index")
    indexWriter.forceMerge(1)
    indexWriter.close()
    directory.close()
    context.system.terminate()
  }

  def receive = {
    case Terminated(actor) =>
      log.debug(s"actor[$actor.path.name] has terminated")
      activeIdx -= 1
      log.debug(s"active index actors = $activeIdx")
      if (activeIdx == 0) self ! PoisonPill
  }

  private def indexFile(xmlFile: String,
                        encoding: String): Unit = {
    log.info("Requesting the ndexing of the file file:" + xmlFile)

    routerIdx.route((xmlFile, encoding), self)
  }

  /**
    * From a decs master file, converts it into a map of (decs code -> descriptors)
    *
    * @return a map where the keys are the decs code (its mfn) and the values, the
              the descriptors in English, Spanish and Protuguese
    */
  private def decx2Map(decsDir: String): Map[Int,Set[String]] = {
    val mst = MasterFactory.getInstance(decsDir).open()
    val map = mst.iterator().asScala.foldLeft[Map[Int,Set[String]]](Map()) {
      case (map,rec) =>
        if (rec.isActive()) {
          val mfn = rec.getMfn()

          // English
          val lst_1 = rec.getFieldList(1).asScala
          val set_1 = lst_1.foldLeft[Set[String]](Set[String]()) {
            case(set, fld) => set + fld.getContent()
          }
          // Spanish
          val lst_2 = rec.getFieldList(2).asScala
          val set_2 = lst_2.foldLeft[Set[String]](set_1) {
            case(set, fld) => set + fld.getContent()
          }
          // Portuguese
          val lst_3 = rec.getFieldList(3).asScala
          val set_3 = lst_3.foldLeft[Set[String]](set_2) {
            case(set, fld) => set + fld.getContent()
          }
          map + ((mfn, set_3))
        } else map
    }
    mst.close()

    map
  }
}

class LuceneIndexActor(indexWriter: IndexWriter,
                       fldIdxNames: Set[String],
                       fldStrdNames: Set[String],
                       decsMap: Map[Int,Set[String]]) extends Actor with ActorLogging {
  val regexp = """\^d\d+""".r
  val doc = new Document()
  val fldMap = fldIdxNames.foldLeft[Map[String,Float]](Map[String,Float]()) {
    case (map,fname) =>
      val split = fname.split(" *: *", 2)
      if (split.length == 1) map + ((split(0), 1f))
      else map + ((split(0), split(1).toFloat))
  }

  def receive = {
    case (fname:String, encoding:String) => {
      log.debug(s"[${self.path.name}] received a requisition to index $fname")
      try {
        IahxXmlParser.getElements(fname, encoding, Set()).zipWithIndex.foreach {
          case (map,idx) => {
            if (idx % 50000 == 0) {
              log.info(s"[$fname] - $idx")
            }
            indexWriter.addDocument(map2doc(map.toMap, doc))
          }
        }
      } catch  {
        case ex: Throwable => log.error(s"[$fname] -${ex.toString()}")
      }
      log.debug(s"[${self.path.name}] finished my task")
    }
    case msg:String =>
      log.debug(s"recebi mensagem [$msg]")
      doc.clear()
      doc.add(new TextField("msg", msg,  Field.Store.YES))
      indexWriter.addDocument(doc)
    case el => log.debug(s"LuceneIndexActor - recebi uma mensagem inesperada [$el]")
  }

  override def postStop(): Unit = {
    log.debug("LuceneIndexActor is now finishing")
  }

  /**
    * Converts a document from a map of fields into a lucene document
    *
    * @param map a document of (field name -> all occurrences of the field)
    * @param doc a work Document object
    * @return a lucene document
    */
  private def map2doc(map: Map[String,List[String]],
                      doc: Document): Document = {
    doc.clear()

    map.foreach {
      case (tag,lst) =>
        // Add decs descriptors
        if (!decsMap.isEmpty && (tag == "mj")) {
          lst.foreach {
            fld => regexp.findFirstIn(fld) match {
              case Some(subd) => {
                decsMap.get(subd.substring(2).toInt) match {
                  case Some(lst2) => lst2.foreach {
                    descr =>
                      val fld = new TextField("decs", descr, Field.Store.YES)
                      doc.add(fld)
                  }
                  case None => ()
                }
              }
              case None => ()
            }
          }
        }
        if (fldIdxNames.contains(tag)) {  // Add indexed fields
          lst.foreach {
            elem => doc.add(new TextField(tag, elem, Field.Store.YES))
          }
        } else if (fldStrdNames.contains(tag)) { // Add stored fields
          lst.foreach {
            elem => doc.add(new StoredField(tag, elem))
          }
        }
    }
    doc
  }
}

object LuceneIndexAkka extends App {

  class Terminator(app: ActorRef) extends Actor with ActorLogging {
    context watch app
    def receive = {
      case Terminated(_) =>
        //log.info("Terminator - application supervisor has terminated, shutting down")
        context.system.terminate()
    }
  }

  private def usage(): Unit = {
    Console.err.println("usage: LuceneIndexAkka" +
      "\n\t<indexPath> - the nmae+path to the lucene index to be created" +
      "\n\t<xmlDir> - directory of xml files used to create the index" +
      "\n\t[-xmlFileFilter=<regExp>] - regular expression used to filter xml files" +
      "\n\t[-indexedFields=<field1>,...,<fieldN>] - xml doc fields to be indexed and stored" +
      "\n\t[-storedFields=<field1>,...,<fieldN>] - xml doc fields to be stored but not indexed" +
      "\n\t[-decs=<dir>] - decs master file directory" +
      "\n\t[-encoding=<str>] - xml file encoding")
    System.exit(1)
  }

  if (args.length < 2) usage()

  val parameters = args.drop(2).foldLeft[Map[String,String]](Map()) {
    case (map,par) => {
      val split = par.split(" *= *", 2)
      map + ((split(0).substring(1), split(1)))
    }
  }

  val indexPath = args(0)
  val xmlDir = args(1)
  val xmlFileFilter = parameters.getOrElse("xmlFileFilter", ".+\\.xml")
  val sIdxFields = parameters.getOrElse("indexedFields", "")
  val fldIdxNames = (if (sIdxFields.isEmpty) Set[String]()
                     else sIdxFields.split(" *, *").toSet)
  val sStrdFields = parameters.getOrElse("storedFields", "")
  val fldStrdNames = (if (sStrdFields.isEmpty) Set[String]()
                      else sStrdFields.split(" *, *").toSet) + "id"

  val decsDir = parameters.getOrElse("decs", "")
  val encoding = parameters.getOrElse("encoding", "ISO-8859-1")

  val system = ActorSystem("Main")
  try {
    val props = Props(classOf[LuceneIndexMain], indexPath, xmlDir, xmlFileFilter,
                                   fldIdxNames, fldStrdNames, decsDir, encoding)
    system.actorOf(props, "app")

    //val app = system.actorOf(props, "app")
    //val stopped: Future[Boolean] = gracefulStop(app, 5 hours)

    //Await.result(stopped, 5 hours)
    //val terminator = system.actorOf(Props(classOf[Terminator], app),
    //                                                "app-terminator")
    //stopped onComplete {
    //  case Success(_) => system.terminate()
    //  case Failure(ex) => system.terminate(); throw ex
    //}
  } catch {
    case NonFatal(e) ⇒ system.terminate(); throw e
  }

  Await.result(system.whenTerminated, 6 hours)
}
