package org.bireme.sd

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem}
import akka.actor.{ExtendedActorSystem, PoisonPill, Props, Terminated}
import akka.routing.{ ActorRefRoutee, Broadcast, RoundRobinRoutingLogic, Router,
                                                   SmallestMailboxRoutingLogic }
import bruma.master._

import java.io.File

import org.apache.lucene.document.{Document,Field,StoredField,TextField}
import org.apache.lucene.index.{IndexWriter,IndexWriterConfig}
import org.apache.lucene.store.FSDirectory

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

class LuceneIndexMain(indexPath: String,
                      xmlDir: String,
                      fldNames: Set[String],
                      decsDir: String,
                      encoding: String) extends Actor with ActorLogging {
  val idxWorkers = 5 // Number of actors to run concurrently

  val analyzer = new NGramAnalyzer(NGSize.ngram_size)
  val directory = FSDirectory.open(new File(indexPath).toPath())
  val config = new IndexWriterConfig(analyzer)
  config.setOpenMode(IndexWriterConfig.OpenMode.CREATE)
  val indexWriter = new IndexWriter(directory, config)
  val (decsDirectory,decsBoost) = if (decsDir.isEmpty()) ("", 0f) else {
    val split = decsDir.split(" *: *", 2)
    if (split.length == 1) (split(0), 1f) else (split(0), split(1).toFloat)
  }
  val decsMap = if (decsDir.isEmpty()) Map[Int,Set[String]]()
                else decx2Map(decsDirectory)
  val routerIdx = {
    val routees = Vector.fill(idxWorkers) {
      val r = context.actorOf(Props(classOf[LuceneIndexActor], indexWriter,
                                                  fldNames, decsMap, decsBoost))
      context watch r
      ActorRefRoutee(r)
    }
    Router(RoundRobinRoutingLogic(), routees)
    //Router(SmallestMailboxRoutingLogic(), routees)
  }
  var activeIdx  = idxWorkers

  (new File(xmlDir)).listFiles().foreach {
    file =>
      val xmlFile = file.getPath()
      if (xmlFile.toLowerCase().endsWith(".xml")) {
        indexFile(xmlFile, encoding)
      }
  }

  // finishing Actors
  routerIdx.route(Broadcast(PoisonPill), self)

  override def postStop(): Unit = {
    log.info("Optimizing index")
    indexWriter.forceMerge(1)
    indexWriter.close()
    directory.close()
  }

  def receive = {
    case Terminated(actor) =>
      //log.info(s"actor[$actor] has terminated")
      activeIdx -= 1
      //log.info(s"active index actors = $activeIdx")
      if (activeIdx == 0) self ! PoisonPill
  }

  private def indexFile(xmlFile: String,
                        encoding: String): Unit = {
    log.info("Indexing file:" + xmlFile)

    routerIdx.route((xmlFile, encoding), self)
  }

  private def decx2Map(decsDir: String): Map[Int,Set[String]] = {
    val mst = MasterFactory.getInstance(decsDir).open()
    val map = mst.iterator().asScala.foldLeft[Map[Int,Set[String]]](Map()) {
      case (map,rec) =>
        if (rec.isActive()) {
          val mfn = rec.getMfn()
          val lst_1 = rec.getFieldList(1).asScala
          val set_1 = lst_1.foldLeft[Set[String]](Set[String]()) {
            case(set, fld) => set + fld.getContent()
          }
          val lst_2 = rec.getFieldList(2).asScala
          val set_2 = lst_2.foldLeft[Set[String]](set_1) {
            case(set, fld) => set + fld.getContent()
          }
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
                       fldNames: Set[String],
                       decsMap: Map[Int,Set[String]],
                       decsBoost: Float) extends Actor with ActorLogging {
  val regexp = """\^d\d+""".r
  val doc = new Document()
  val indexDecs = !decsMap.isEmpty
  val fldMap = fldNames.foldLeft[Map[String,Float]](Map[String,Float]()) {
    case (map,fname) =>
      val split = fname.split(" *: *", 2)
      if (split.length == 1) map + ((split(0), 1f))
      else map + ((split(0), split(1).toFloat))
  }

  def receive = {
    case (fname:String, encoding:String) => {
      IahxXmlParser.getElements(fname, encoding, Set()).zipWithIndex.foreach {
        case (map,idx) => {
          if (idx % 50000 == 0) {
            log.info(s"[$fname] - $idx")
          }
          indexWriter.addDocument(map2doc(map.toMap, doc))
        }
      }
    }
    case msg:String =>
      println(s"recebi mensagem [$msg]")
      doc.clear()
      doc.add(new TextField("msg", msg,  Field.Store.YES))
      indexWriter.addDocument(doc)
    case el => println(s"LuceneIndexActor - recebi uma mensagem inesperada [$el]")
  }

  override def postStop(): Unit = {
    //log.info("LuceneIndexActor is now finishing")
  }

  private def map2doc(map: Map[String,List[String]],
                      doc: Document): Document = {
    doc.clear()

    map.foreach {
      case (tag,lst) =>
        if (indexDecs && (tag == "mj")) {
          lst.foreach {
            fld => regexp.findFirstIn(fld) match {
              case Some(subd) => {
                decsMap.get(subd.substring(2).toInt) match {
                  case Some(lst2) => lst2.foreach {
                    descr =>
                      val fld = new TextField("decs", descr, Field.Store.YES)
                      fld.setBoost(decsBoost)
                      doc.add(fld)
                  }
                  case None => ()
                }
              }
              case None => ()
            }
          }
        }
        if (fldMap.contains(tag)) {
          val boost = fldMap(tag)
          lst.foreach {
            elem =>
              val fld = new TextField(tag, elem, Field.Store.YES)
              fld.setBoost(boost)
              doc.add(fld)
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
    Console.err.println("usage: LuceneIndexAkka <indexPath> <xmlDir>" +
    "\n\t[-fields=<field1>[:<boost>],...,<fieldN>[:<boost>]]" +
    "\n\t[-decs=<dir>[:<boost>]]" +
    "\n\t[-encoding=<str>]")
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

  val decsDir = parameters.getOrElse("decs", "")
  val encoding = parameters.getOrElse("encoding", "ISO-8859-1")

  val system = ActorSystem("Main")
  try {
    val props = Props(classOf[LuceneIndexMain], args(0), args(1), fldNames,
                                                              decsDir, encoding)
    val app = system.actorOf(props, "app")
    val terminator = system.actorOf(Props(classOf[Terminator], app),
                                                               "app-terminator")
  } catch {
    case NonFatal(e) ⇒ system.terminate(); throw e
  }
}