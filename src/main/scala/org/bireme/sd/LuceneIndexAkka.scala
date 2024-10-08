/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.sd

import java.io.File
import java.nio.file.Path
import java.text.{DateFormat, SimpleDateFormat}
import java.util.regex.{Matcher, Pattern}

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props, Terminated}
import akka.event.Logging
import akka.routing.{ActorRefRoutee, Broadcast, RoundRobinRoutingLogic, Router}
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.document.{Document, Field, StoredField, StringField, TextField}
import org.apache.lucene.index._
import org.apache.lucene.search.{IndexSearcher, MatchAllDocsQuery, ScoreDoc}
import org.apache.lucene.store.FSDirectory
import org.bireme.sd.service.Conf
import org.bireme.sd.service.Conf.excludeDays
import org.h2.mvstore.{MVMap, MVStore}

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

// http://iahx-idx02.bireme.br:8986/solr5/admin.html#/
// http://iahx-idx02.bireme.br:8986/solr5/admin/cores?action=STATUS
// grep -Po "(?<=lastModified\">[^<]+"

case class Finishing()

class LuceneIndexMain(indexPath: String,
                      OneWordDecsIndexPath: String,
                      modifiedIndexPath: String,
                      xmlDir: String,
                      xmlFileFilter: String,
                      fldIdxNames: Set[String],
                      fldStrdNames: Set[String],
                      encoding: String,
                      fullIndexing: Boolean) extends Actor with ActorLogging {
  context.system.eventStream.setLogLevel(Logging.InfoLevel)

  private val idxWorkers = Runtime.getRuntime.availableProcessors() // Number of actors to run concurrently
  private val analyzer: Analyzer = new NGramAnalyzer(NGSize.ngram_min_size, NGSize.ngram_max_size)
  private val indexPathTrim: String = indexPath.trim
  private val indexPath1: String = if (indexPathTrim.endsWith("/")) indexPathTrim.substring(0, indexPathTrim.length - 1)
                                   else indexPathTrim
  private val indexPath2: Path = new File(indexPath1).toPath
  private val directory: FSDirectory = FSDirectory.open(indexPath2)
  private val config: IndexWriterConfig = new IndexWriterConfig(analyzer)
  if (fullIndexing) config.setOpenMode(IndexWriterConfig.OpenMode.CREATE)
  else config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND)
  private val indexWriter: IndexWriter = new IndexWriter(directory, config)

  private val modFile = new File(modifiedIndexPath)
  if (!modFile.exists()) modFile.mkdirs()
  if (fullIndexing) new File(modFile, "docLastModified.db").delete()

  private val docLastModified: MVStore = new MVStore.Builder().fileName(s"$modifiedIndexPath/docLastModified.db")
    .compress().open()
  private val lastModifiedDoc: MVMap[String, Long] = docLastModified.openMap("modDoc")
  if (fullIndexing) lastModifiedDoc.clear()

  // Only index documents which are older than that date
  private val endDate: Long = Tools.getIahxModificationTime - Tools.daysToTime(excludeDays)

  private val routerIdx: Router = createActors()
  private var activeIdx: Int = idxWorkers

  // Create similar docs index
  createSimDocsIndex(xmlDir)

  // finishing Actors
  routerIdx.route(Broadcast(Finishing()), self)


  private def createActors(): Router = {
    val decsMap: Map[Int, Set[String]] = getDescriptors(OneWordDecsIndexPath)

    val routees: Vector[ActorRefRoutee] = Vector.fill(idxWorkers) {
      val r: ActorRef = context.actorOf(Props(classOf[LuceneIndexActor], indexWriter,
        fldIdxNames, fldStrdNames, decsMap, lastModifiedDoc, endDate))
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
          matcher.reset(file.getName)
          if (matcher.matches) indexFile(file.getPath, encoding)
        }
    }
  }

  override def postStop(): Unit = {
    log.info("Optimizing index 'sdIndex' - begin")
    indexWriter.forceMerge(1)
    indexWriter.close()
    directory.close()
    analyzer.close()
    log.info("Optimizing index 'sdIndex - end'")
    docLastModified.close()
    context.system.terminate()
    ()
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
    * Create a map of DeCS descriptors from a Lucene index
    *
    * @param decsIndexPath Lucene index with DeCS documents
    * @return a map where the keys are the decs code (its mfn) and the values, the
    *         the descriptors in English, Spanish and Portuguese
    */
  private def getDescriptors(decsIndexPath: String): Map[Int, Set[String]] = {
    val directory: FSDirectory = FSDirectory.open(new File(decsIndexPath).toPath)
    val ireader: DirectoryReader = DirectoryReader.open(directory)
    val isearcher: IndexSearcher = new IndexSearcher(ireader)
    val query: MatchAllDocsQuery = new MatchAllDocsQuery()
    val hits: Array[ScoreDoc] = isearcher.search(query, Integer.MAX_VALUE).scoreDocs
    val descriptors: Map[Int, Set[String]] = hits.foldLeft(Map[Int, Set[String]]()) {
      case (map, hit) =>
        val doc: Document = ireader.storedFields().document(hit.doc) //ireader.document(hit.doc)
        val id: Int = doc.get("id").toInt
        val descr: Set[String] = doc.getValues("descriptor").toSet
        map + (id -> descr)
    }
    ireader.close()
    directory.close()
    descriptors
  }
}

class LuceneIndexActor(indexWriter: IndexWriter,
                       fldIdxNames: Set[String],
                       fldStrdNames: Set[String],
                       decsMap: Map[Int,Set[String]],
                       lastModifiedDoc: MVMap[String, Long],
                       endDate: Long) extends Actor with ActorLogging {
  private val regexp: Regex = """\^d\d+""".r
  private val checkXml: CheckXml = new CheckXml()
  private val formatter: DateFormat = new SimpleDateFormat("yyyyMMdd")

  def receive: PartialFunction[Any, Unit] = {
    case (fname:String, encoding:String) =>
      log.debug(s"[${self.path.name}] received a requisition to index $fname")
      try {
        checkXml.check(fname) match {
          case Some(errMess) => log.error(s"skipping document => file:[$fname] - $errMess")
          case None =>
            IahxXmlParser.getElements(fname, encoding, Set[String]()).zipWithIndex.foreach {
              case (mmap: mutable.Map[String, List[String]], idx) =>
                Try(createNewDocument(fname, mmap.toMap)) match {
                  case Success(_) => ()
                  case Failure(ex) => log.error(s"skipping document => file:[$fname] - ${ex.getMessage}")
                }
                if (idx % 50000 == 0) {
                  indexWriter.flush()
                  log.info(s"[$fname] - $idx")
                }
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
        Try {
          doc.get("update_date").foreach {
            lst =>
              lst.headOption.foreach {
                upd =>
                  if (upd.nonEmpty) {
                    val updTimeNew: Long = formatter.parse(upd).getTime
                    if (updTimeNew <= endDate) {  // include if document is not too new compared to the iahx index processing time
                      Option(lastModifiedDoc.get(sid)) match {
                        case Some(mdate) =>
                          if (updTimeNew > mdate) {
                            indexWriter.updateDocument(new Term("id", sid), map2docExt(doc)) // update the document in the index
                            lastModifiedDoc.put(sid, updTimeNew)
                          }
                        case None =>
                          indexWriter.addDocument(map2docExt(doc)) // insert the document into the index
                          lastModifiedDoc.put(sid, updTimeNew)
                      }
                    }
                  }
              }
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
        else if (tag.equals("db")) doc.add(new StringField("db", lst.head, Field.Store.YES))
        else if (tag.equals("instance")) doc.add(new StringField("instance", lst.head, Field.Store.YES))
        else if (tag.equals("update_date")) doc.add(new StringField("update_date", lst.head, Field.Store.YES))
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
      "\n\t-sdIndex=<path> - the name+path to the lucene index of similar documents to be created" +
      "\n\t-idIndex=<path> - the name+path index of already indexed/stored documents to be used or created" +
      "\n\t-oneWordIndexPath=<path> - the name+path to the lucene index of DeCS descriptors to be used" +
      "\n\t-xml=<path> - directory of xml files used to create the index" +
      "\n\t[-xmlFileFilter=<regExp>] - regular expression used to filter xml files" +
      "\n\t[-indexedFields=<field1>,...,<fieldN>] - xml doc fields to be indexed and stored" +
      "\n\t[-storedFields=<field1>,...,<fieldN>] - xml doc fields to be stored but not indexed" +
      "\n\t[-encoding=<str>] - xml file encoding" +
      "\n\t[--fullIndexing] - if present, recreate the indexes from scratch")
    System.exit(1)
  }

  if (args.length < 4) usage()

  private val parameters = args.foldLeft[Map[String,String]](Map()) {
    case (map,par) =>
      val split = par.split(" *= *", 2)
      if (split.length == 1) map + ((split(0).substring(2), ""))
      else map + ((split(0).substring(1), split(1)))
  }

  private val sdIndex = parameters("sdIndex")
  private val oneWordIndexPath = parameters("oneWordIndexPath")
  private val idIndex = parameters("idIndex")
  private val xml = parameters("xml")
  private val xmlFileFilter = parameters.getOrElse("xmlFileFilter", ".+\\.xml")
  private val sIdxFields = parameters.getOrElse("indexedFields", "")
  private val fldIdxNames = if (sIdxFields.isEmpty) Conf.idxFldNames
                            else sIdxFields.split(" *, *").toSet
  private val storedFields = parameters.getOrElse("storedFields", "")
  private val fldStrdNames = if (storedFields.isEmpty) Set[String]()
                             else storedFields.split(" *, *").toSet
  private val encoding = parameters.getOrElse("encoding", "ISO-8859-1")
  private val fullIndexing = parameters.contains("fullIndexing")

  private val system: ActorSystem = ActorSystem("Main")
  try {
    val props: Props = Props(classOf[LuceneIndexMain], sdIndex, oneWordIndexPath, idIndex,
                      xml, xmlFileFilter, fldIdxNames, fldStrdNames, encoding, fullIndexing)
    system.actorOf(props, "app")
  } catch {
    case NonFatal(e) =>
      println("---------------------------------------------------------------")
      println(s"Application Error: ${e.toString}")
      println("---------------------------------------------------------------")
      e.printStackTrace()
      system.terminate(); throw e
  }

  Await.result(system.whenTerminated, 24.hours)

  println("*** Indexing finished!")
}
