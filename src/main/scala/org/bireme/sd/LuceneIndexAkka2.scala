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
import akka.routing.{ ActorRefRoutee, Broadcast, RoundRobinRoutingLogic, Router,
                                                   SmallestMailboxRoutingLogic }
import java.io.File

import org.apache.lucene.document.{Document,Field,TextField}
import org.apache.lucene.index.{IndexWriter,IndexWriterConfig}
import org.apache.lucene.store.FSDirectory

import scala.util.control.NonFatal

case object FinishMessage

/***
* AVISO - Erro de algoritmo - corrigir
          java.lang.OutOfMemoryError: GC overhead limit exceeded
***/

class LuceneIndexMain2(indexPath: String,
                       xmlDir: String,
                       fldNames: Set[String],
                       encoding: String) extends Actor with ActorLogging {
  val xmlWorkers = 3 // Number of actors to run concurrently
  val idxWorkers = 4 // Number of actors to run concurrently

  val analyzer = new NGramAnalyzer(NGSize.ngram_size)
  val directory = FSDirectory.open(new File(indexPath).toPath())
  val config = new IndexWriterConfig(analyzer)
  config.setOpenMode(IndexWriterConfig.OpenMode.CREATE)
  val indexWriter = new IndexWriter(directory, config)
  val routerIdx = {
    val routees = Vector.fill(idxWorkers) {
      val r = context.actorOf(Props(classOf[LuceneIndexActor2], indexWriter,
                                                                      fldNames))
      context watch r
      ActorRefRoutee(r)
    }
    Router(RoundRobinRoutingLogic(), routees)
    //Router(SmallestMailboxRoutingLogic(), routees)
  }
  val routerXml = {
    val routees = Vector.fill(xmlWorkers) {
      val r = context.actorOf(Props(classOf[XmlParserActor2], routerIdx,
                                                                   indexWriter))
      context watch r
      ActorRefRoutee(r)
    }
    //Router(RoundRobinRoutingLogic(), routees)
    Router(SmallestMailboxRoutingLogic(), routees)
  }
  var activeIdx  = idxWorkers
  var activeXml  = xmlWorkers

  (new File(xmlDir)).listFiles().foreach {
    file =>
      val xmlFile = file.getPath()
      if (xmlFile.toLowerCase().endsWith(".xml")) {
        indexFile(xmlFile, encoding)
      }
  }

  // finishing Actors
  routerXml.route(Broadcast(FinishMessage), self)

  override def postStop(): Unit = {
    log.info("Optimizing index")
    indexWriter.forceMerge(1)
    indexWriter.close()
    directory.close()
  }

  def receive = {
    case Terminated(_) =>
      //log.info(s"actor[$actor] has terminated")
      activeIdx -= 1
      //log.info(s"active index actors = $activeIdx")
      if (activeIdx == 0) self ! PoisonPill
    case FinishMessage => {
      activeXml -= 1
      //log.info(s"active xml actors = $activeXml")
      if (activeXml == 0) routerIdx.route(Broadcast(PoisonPill), self)
    }
  }

  def indexFile(xmlFile: String,
                encoding: String): Unit = {
    log.info("Indexing file:" + xmlFile)

    routerXml.route((xmlFile, encoding), self)
  }
}

class XmlParserActor2(routerIdx: Router,
                      indexWriter: IndexWriter) extends Actor with ActorLogging {
  def receive = {
    case (fname:String, encoding:String) => {
      IahxXmlParser.getElements(fname, encoding, Set()).zipWithIndex.foreach {
        case (map,idx) => {
          //println(s"map=$map")
          if (idx % 50000 == 0) {
            log.info(s"[$fname] - $idx")
            indexWriter.commit()
          }
          routerIdx.route(map.toMap, self)
        }
      }
    }
    case FinishMessage =>
      sender ! FinishMessage
    case el => println(s"XmlParserActor2 - recebi uma mensagem inesperada [$el]")
  }
}

class LuceneIndexActor2(indexWriter: IndexWriter,
                        fldNames: Set[String]) extends Actor with ActorLogging {
  val doc = new Document()

  def receive = {
    case map: Map[String,List[String]] =>
                                      indexWriter.addDocument(map2doc(map, doc))
    case msg:String =>
      println(s"recebi mensagem [$msg]")
      doc.clear()
      doc.add(new TextField("msg", msg,  Field.Store.YES))
      indexWriter.addDocument(doc)
    case el => println(s"LuceneIndexActor2 - recebi uma mensagem inesperada [$el]")
  }

  override def postStop(): Unit = {
    //log.info("LuceneIndexActor2 is now finishing")
  }

  private def map2doc(map: Map[String,List[String]],
                      doc: Document): Document = {
    doc.clear()

    map.foreach {
      case (tag,lst) =>
        if (fldNames(tag)) {
          lst.foreach {
            elem => doc.add(new TextField(tag, elem,  Field.Store.YES))
          }
        } else {
          /*lst.foreach {
            elem => doc.add(new StoredField(tag, elem))
          }*/
        }
    }
    doc
  }
}

object LuceneIndexAkka2 extends App {

  class Terminator(app: ActorRef) extends Actor with ActorLogging {
    context watch app
    def receive = {
      case Terminated(_) =>
        //log.info("Terminator - application supervisor has terminated, shutting down")
        context.system.terminate()
    }
  }

  private def usage(): Unit = {
    Console.err.println("usage: LuceneIndexAkka2 <indexPath> <xmlDir>" +
    "[-fields=<field1>,...,<fieldN>] [-encoding=<str>]")
    System.exit(1)
  }

  if (args.length < 2) usage()

  val parameters = args.drop(2).foldLeft[Map[String,String]](Map()) {
    case (map,par) => {
      val split = par.split(" *= *", 2)
      map + ((split(0).substring(1), split(1)))
    }
  }
  val sFields = parameters.getOrElse("fields", "")
  val fldNames = (if (sFields.isEmpty) Set[String]()
                 else sFields.split(" *, *").toSet) + "id"

  val encoding = parameters.getOrElse("encoding", "ISO-8859-1")

  val system = ActorSystem("Main")
  try {
    val props = Props(classOf[LuceneIndexMain2], args(0), args(1), fldNames,
                                                                       encoding)
    val app = system.actorOf(props, "app")
    val terminator = system.actorOf(Props(classOf[Terminator], app),
                                                               "app-terminator")
  } catch {
    case NonFatal(e) ⇒ system.terminate(); throw e
  }
}
