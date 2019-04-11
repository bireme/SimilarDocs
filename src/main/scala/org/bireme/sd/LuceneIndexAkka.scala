/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.sd

import java.io.File
import java.nio.file.Path
import java.{lang, util}
import java.util.regex.{Matcher, Pattern}
import java.util.{GregorianCalendar, TimeZone}

import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill, Props, Terminated}
import akka.event.Logging
import akka.routing.{ActorRefRoutee, Broadcast, RoundRobinRoutingLogic, Router}
import bruma.master._
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.core.KeywordAnalyzer
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper
import org.apache.lucene.document.{DateTools, Document, Field, StoredField, StringField, TextField}
import org.apache.lucene.index.{IndexWriter, IndexWriterConfig, Term}
import org.apache.lucene.store.FSDirectory
import org.bireme.sd.service.Conf
import org.mapdb.{DB, DBMaker, HTreeMap, Serializer}

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.control.NonFatal
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

case class Finishing()

class LuceneIndexMain(indexPath: String,
                      decsIndexPath: String,
                      idsIndexPath: String,
                      xmlDir: String,
                      xmlFileFilter: String,
                      fldIdxNames: Set[String],
                      fldStrdNames: Set[String],
                      decsDir: String,
                      encoding: String,
                      fullIndexing: Boolean) extends Actor with ActorLogging {
  context.system.eventStream.setLogLevel(Logging.InfoLevel)

  val idxWorkers = 10 // Number of actors to run concurrently

  val ngAnalyzer: NGramAnalyzer = new NGramAnalyzer(NGSize.ngram_min_size, NGSize.ngram_max_size)
  val analyzerPerField: util.Map[String, Analyzer] = Map[String,Analyzer](
                                 "entrance_date" -> new KeywordAnalyzer()).asJava
  val analyzer: PerFieldAnalyzerWrapper = new PerFieldAnalyzerWrapper(ngAnalyzer, analyzerPerField)
  val indexPathTrim: String = indexPath.trim
  val indexPath1: String = if (indexPathTrim.endsWith("/")) indexPathTrim.substring(0, indexPathTrim.length - 1)
                           else indexPathTrim
  val indexPath2: Path = new File(indexPath1).toPath
  val directory: FSDirectory = FSDirectory.open(indexPath2)
  val config: IndexWriterConfig = new IndexWriterConfig(analyzer)
  if (fullIndexing) config.setOpenMode(IndexWriterConfig.OpenMode.CREATE)
  else config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND)
  val indexWriter: IndexWriter = new IndexWriter(directory, config)

  val dbFiles: DB = DBMaker.fileDB(s"$idsIndexPath/fileLastModified.db").closeOnJvmShutdown().make
  val db: DB = DBMaker.fileDB(s"$idsIndexPath/allDocIds.db").closeOnJvmShutdown().make
  val allDocIds: HTreeMap.KeySet[Integer] = db.hashSet("idSet", Serializer.INTEGER).createOrOpen()
  val lastModified: HTreeMap[String, lang.Long] = db.hashMap("modFile", Serializer.STRING, Serializer.LONG).createOrOpen()
  if (fullIndexing) {
    allDocIds.clear()
    lastModified.clear()
  }
  val routerIdx: Router = createActors()
  var activeIdx: Int = idxWorkers

  // Create decs index
  log.info("Creating decs index")
  OneWordDecs.createIndex(decsDir, decsIndexPath)

  // Create similar docs index
  createSimDocsIndex(xmlDir)

  // finishing Actors
  routerIdx.route(Broadcast(Finishing()), self)

  private def createActors(): Router = {
    val now: GregorianCalendar = new GregorianCalendar(TimeZone.getDefault)
    val today: String = DateTools.dateToString(DateTools.round(now.getTime, DateTools.Resolution.DAY),
      DateTools.Resolution.DAY)
    val decsMap: Map[Int, Set[String]] = if (decsDir.isEmpty) Map[Int,Set[String]]()
    else decx2Map(decsDir)

    val routees = Vector.fill(idxWorkers) {
      val r = context.actorOf(Props(classOf[LuceneIndexActor], today, indexWriter,
        fldIdxNames ++ Set("entrance_date", "id"),
        fldStrdNames, decsMap, allDocIds))
      context watch r
      ActorRefRoutee(r)
    }

    Router(RoundRobinRoutingLogic(), routees)
  }

  private def createSimDocsIndex(xmlDir: String): Unit = {
    val matcher: Matcher = Pattern.compile(xmlFileFilter).matcher("")

    new File(xmlDir).listFiles().sorted.foreach {
      file =>
        if (file.isFile) {
          val fname = file.getName
          matcher.reset(fname)
          if (matcher.matches) {
            val fileLastModified: Long = file.lastModified()
            Option(lastModified.get(fname)) match {
              case Some(modified: lang.Long) =>
                if (fileLastModified > modified) {
                  indexFile(file.getPath, encoding)
                  lastModified.put(fname, fileLastModified)
                }
              case _ =>
                indexFile(file.getPath, encoding)
                lastModified.put(fname, fileLastModified)
            }
          }
        }
    }
  }

  override def postStop(): Unit = {
    log.info("Optimizing index 'sdIndex' - begin")
    indexWriter.forceMerge(1)
    indexWriter.close()
    directory.close()
    db.close()
    log.info("Optimizing index 'sdIndex - end'")
    context.system.terminate()
  }

  def receive: PartialFunction[Any, Unit] = {
    case Terminated(actor) =>
      log.debug(s"actor[${actor.path.name}] has terminated")
      activeIdx -= 1
      log.debug(s"active index actors = $activeIdx")
      if (activeIdx == 0) self ! PoisonPill
  }

  private def indexFile(xmlFile: String,
                        encoding: String): Unit = {
    log.info("File to be indexed:" + xmlFile)

    routerIdx.route((xmlFile, encoding), self)
  }

  /**
    * From a decs master file, converts it into a map of (decs code -> descriptors)
    *
    * @return a map where the keys are the decs code (its mfn) and the values, the
              the descriptors in English, Spanish and Portuguese
    */
  private def decx2Map(decsDir: String): Map[Int,Set[String]] = {
    val mst = MasterFactory.getInstance(decsDir).open()
    val map = mst.iterator().asScala.foldLeft[Map[Int,Set[String]]](Map()) {
      case (map2,rec) =>
        if (rec.isActive) {
          val mfn = rec.getMfn

          // English
          val lst_1 = rec.getFieldList(1).asScala
          val set_1 = lst_1.foldLeft[Set[String]](Set[String]()) {
            case(set, fld) => set + fld.getContent
          }
          // Spanish
          val lst_2 = rec.getFieldList(2).asScala
          val set_2 = lst_2.foldLeft[Set[String]](set_1) {
            case(set, fld) => set + fld.getContent
          }
          // Portuguese
          val lst_3 = rec.getFieldList(3).asScala
          val set_3 = lst_3.foldLeft[Set[String]](set_2) {
            case(set, fld) => set + fld.getContent
          }
          map2 + ((mfn, set_3))
        } else map2
    }
    mst.close()

    map
  }
}

class LuceneIndexActor(today: String,
                       //todaySeconds: String,
                       indexWriter: IndexWriter,
                       fldIdxNames: Set[String],
                       fldStrdNames: Set[String],
                       decsMap: Map[Int,Set[String]],
                       allDocIds: HTreeMap.KeySet[Integer]) extends Actor with ActorLogging {
  val regexp: Regex = """\^d\d+""".r
  val checkXml: CheckXml = new CheckXml()
  val fieldsIndexNames: Set[String] = if ((fldIdxNames == null) || fldIdxNames.isEmpty) Conf.idxFldNames
                         else fldIdxNames
  val fldMap: Map[String, Float] = fieldsIndexNames.foldLeft[Map[String,Float]](Map[String,Float]()) {
    case (map,fname) =>
      val split = fname.split(" *: *", 2)
      if (split.length == 1) map + ((split(0), 1f))
      else map + ((split(0), split(1).toFloat))
  }

  def receive: PartialFunction[Any, Unit] = {
    case (fname:String, encoding:String) =>
      log.debug(s"[${self.path.name}] received a requisition to index $fname")
      try {
        checkXml.check(fname) match {
          case Some(errMess) => log.error(s"skipping document => file:[$fname] - $errMess")
          case None =>
            IahxXmlParser.getElements(fname, encoding, Set()).zipWithIndex.foreach {
              case (set,idx) =>
                if (idx % 50000 == 0) log.info(s"[$fname] - $idx")
                createNewDocument(fname, set.toMap)
            }
        }
      } catch {
        case ex: Throwable => log.error(s"skipping file: [$fname] -${ex.toString}")
      }
      log.debug(s"[${self.path.name}] finished my task")
    case _:Finishing => self ! PoisonPill
    case el => log.debug(s"LuceneIndexActor - recebi uma mensagem inesperada [$el]")
  }

  override def postStop(): Unit = {
    log.debug(s"LuceneIndexActor[${self.path.name}] is now finishing")
  }

  /**
    * Create a new document if the id is not already present in that index or update an old one
    * @param fname the name of file with this document
    * @param doc the document to be stored and indexed
    */
  private def createNewDocument(fname: String,
                                doc: Map[String, List[String]]): Unit = {
    doc.get("id") match {
      case Some(id: Seq[String]) =>
        val sid = id.head
        val hid: Int = sid.hashCode
        val ndoc: Map[String, List[String]] = doc + ("entrance_date" -> List(today)) // add 'entrance_date' field

        Try {
          if (allDocIds.contains(hid)) {
            indexWriter.updateDocument(new Term("id", sid), map2docExt(ndoc)) // update the document in the index
          } else {
            indexWriter.addDocument(map2docExt(ndoc)) // insert the document into the index
            allDocIds.add(hid)
          }
        } match {
          case Success(_) => ()
          case Failure(ex) => log.error(s"skipping document => file:[$fname] id:[$sid] -${ex.toString}")
        }
      case Some(_) | None => ()
    }
  }

  /**
    * Converts a document from a map of fields into a lucene document (all then will be stored but
    * no indexed. Create a field '_indexed_' with fldIdxNames that will be indexed.
    *
    * @param map a document of (field name -> all occurrences of the field)
    * @return a lucene document
    */
  private def map2docExt(map: Map[String,List[String]]): Document = {
    val doc = new Document()
    val sbuilder = new StringBuilder

    map.foreach {
      case (xtag, lst) =>
        val tag: String = xtag.toLowerCase

        if (decsMap.nonEmpty && (tag == "mj")) {  // Add decs descriptors
          lst.foreach {
            fld => regexp.findFirstIn(fld).foreach {
              subd =>
                decsMap.get(subd.substring(2).toInt).foreach {
                  lst2 => lst2.foreach {
                    descr =>
                      val fld = new StoredField("decs", descr)
                      sbuilder.append( " " + descr)
                      doc.add(fld)
                  }
                }
            }
          }
        }
        else if (tag.equals("id")) doc.add(new StringField("id", lst.head, Field.Store.YES))
        else {
          if (fldIdxNames.contains(tag)) {  // Add indexed + stored fields as stored fields
            lst.foreach {
              elem =>
                val fld = if (elem.length < 10000) elem // Bug during indexing. Fix in future. Sorry!
                          else elem.substring(0,10000)
                sbuilder.append( " " + fld)
                doc.add(new StoredField(tag, fld))
            }
          }
          if (fldStrdNames.contains(tag)) { // Add stored fields
            lst.foreach {
              elem => doc.add(new StoredField(tag, elem))
            }
          }
        }
    }
    doc.add(new TextField(Conf.indexedField, sbuilder.toString, Field.Store.NO)) // Add _indexed_ field as indexed field
    doc
  }
}

object LuceneIndexAkka extends App {

  /*class Terminator(app: ActorRef) extends Actor with ActorLogging {
    context watch app
    def receive = {
      case Terminated(_) =>
        //log.info("Terminator - application supervisor has terminated, shutting down")
        context.system.terminate()
    }
  }*/

  private def usage(): Unit = {
    Console.err.println("usage: LuceneIndexAkka" +
      "\n\t<indexPath> - the name+path to the lucene index to be created" +
      "\n\t<decsIndexPath> - the name+path to the lucene index to be created" +
      "\n\t<idIndexPath> - the ids index of already indexed/stored documents" +
      "\n\t<xmlDir> - directory of xml files used to create the index" +
      "\n\t[-xmlFileFilter=<regExp>] - regular expression used to filter xml files" +
      "\n\t[-indexedFields=<field1>,...,<fieldN>] - xml doc fields to be indexed and stored" +
      "\n\t[-storedFields=<field1>,...,<fieldN>] - xml doc fields to be stored but not indexed" +
      "\n\t[-decs=<dir>] - decs master file directory" +
      "\n\t[-encoding=<str>] - xml file encoding" +
      "\n\t[--fullIndexing] - if present, recreate the indexes from scratch")
    System.exit(1)
  }

  if (args.length < 4) usage()

  val parameters = args.drop(4).foldLeft[Map[String,String]](Map()) {
    case (map,par) =>
      val split = par.split(" *= *", 2)
      if (split.length == 1) map + ((split(0).substring(2), ""))
      else map + ((split(0).substring(1), split(1)))
  }

  val indexPath = args(0)
  val decsIndexPath = args(1)
  val idIndexPath = args(2)
  val xmlDir = args(3)
  val xmlFileFilter = parameters.getOrElse("xmlFileFilter", ".+\\.xml")
  val sIdxFields = parameters.getOrElse("indexedFields", "")
  val fldIdxNames = if (sIdxFields.isEmpty) Conf.idxFldNames
                     else sIdxFields.split(" *, *").toSet
  val sStrdFields = parameters.getOrElse("storedFields", "")
  val fldStrdNames = if (sStrdFields.isEmpty) Set[String]()
                      else sStrdFields.split(" *, *").toSet

  val decsDir = parameters.getOrElse("decs", "")
  val encoding = parameters.getOrElse("encoding", "ISO-8859-1")
  val fullIndexing = parameters.contains("fullIndexing")

  val system = ActorSystem("Main")
  try {
    val props = Props(classOf[LuceneIndexMain], indexPath, decsIndexPath, idIndexPath,
                      xmlDir, xmlFileFilter, fldIdxNames, fldStrdNames, decsDir, encoding,
                      fullIndexing)
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
    case NonFatal(e) =>
      println("---------------------------------------------------------------")
      println(s"Application Error: ${e.toString}")
      println("---------------------------------------------------------------")
      e.printStackTrace()
      system.terminate(); throw e
  }

  Await.result(system.whenTerminated, 24 hours)
}
