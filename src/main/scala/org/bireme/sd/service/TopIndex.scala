/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.sd.service

import java.nio.file.Paths
import java.text.{DateFormat, SimpleDateFormat}
import java.util.{Calendar, Date}

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.document._
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.store.FSDirectory
import org.bireme.sd.{DocumentIterator, NGSize, NGramAnalyzer, SimDocsSearch, Tools}

import scala.jdk.CollectionConverters._
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


/** This class represents a personal service document that indexed by Lucene
  * engine. Each document has two kinds of fields:
  * id:  Unique identifier.
  * the other fields are profiles where the name of the field is the profile name
  * and its content is a sentence used to look for similar documents stored
  * at documents in SDIndex (Similar Documents Lucene Index).
  *
  * @param simSearch similar documents search engine object
  * @param topIndexPath path/name of the TopIndex Lucene index
  *
  * author: Heitor Barbieri
  * date: 20170601
*/
class TopIndex(simSearch: SimDocsSearch,
               topIndexPath: String) {
  require(simSearch != null)
  require((topIndexPath != null) && topIndexPath.trim.nonEmpty)

  val idFldName = "id"                  // Profile identifier
  val userFldName = "user"              // Personal service user id
  val nameFldName = "prof_name"         // Profile name
  val contentFldName = "prof_content"   // Profile content

  private val creationFldName = "creation_time" // Profile creation time
  private val updateFldName = "update_time"     // Profile update time
  private val sdIdFldName = "sd_id"             // Similar document Lucene doc id

  private val deltaTime: Long =  1000 * 60 * 60 * 2 // 2 hours // 8 hours
  private val formatter: DateFormat = new SimpleDateFormat("yyyyMMdd")

  private val lcAnalyzer: LowerCaseAnalyzer = new LowerCaseAnalyzer(true)
  private val ngAnalyzer: Analyzer = new NGramAnalyzer(NGSize.ngram_min_size, NGSize.ngram_max_size)
  private val topDirectory: FSDirectory = FSDirectory.open(Paths.get(topIndexPath))
  private val topWriter: IndexWriter =  new IndexWriter(topDirectory, new IndexWriterConfig(lcAnalyzer).
                                  setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND))
  topWriter.commit()

  private var finishing: Boolean = false  // Flag to stop asynchronous update
  private var updating: Boolean = false   // Flag to indicate if there is an executing asynchronous update

  /**
    * Closes all open resources
    */
  def close(): Unit = {
    finishing = true
    while (updating) {
      assert(finishing)
      Thread.sleep(5000)
      println(s"${new Date} - close() - waiting update to finished. updating=$updating")
    }
    assert(!updating)
    topWriter.close()
    topDirectory.close()
    lcAnalyzer.close()
    ngAnalyzer.close()
    println(s"${new Date} - close() finished!")
  }

  /**
    * Add/update a profile instance to a personal services document
    *
    * @param user personal services document identifier
    * @param name profile name
    * @param content profile content
    */
  def addProfile(user: String,
                 name: String,
                 content: String): Unit = {
    require((user != null) && user.trim.nonEmpty)
    require((name != null) && name.trim.nonEmpty)
    require((content != null) && content.trim.nonEmpty)

    val tuser: String = user.trim()
    val tname: String = name.trim()
    val id: String = s"${tuser}_$tname"
    val updateTime: Long = 0L //new Date().getTime

    // Retrieves or creates the personal service document
    val (doc, isNew) = getDocuments(idFldName, id) match {
      case Some(lst) =>
        val doc2: Document = lst.head
        doc2.removeField(idFldName)   // Avoid Lucene makes id tokenized (workarround)
        doc2.removeField(userFldName) // Avoid Lucene makes id tokenized (workarround)
        doc2.removeField(updateFldName)
        (doc2, false)
      case None =>
        val doc2: Document = new Document()
        doc2.add(new StoredField(nameFldName, tname))
        doc2.add(new StoredField(creationFldName, updateTime))
        (doc2, true)
    }

    // Add id and user fields
    doc.add(new StringField(idFldName, id, Field.Store.YES))
    doc.add(new StringField(userFldName, tuser, Field.Store.YES))

    // Add update_time field
    doc.add(new LongPoint(updateFldName, updateTime))
    doc.add(new StoredField(updateFldName, updateTime))

    // Add profile content field and similar docs id fields to the document
    val tokens: Map[String, Int] = Tools.getTokens(content, ngAnalyzer, sort=false)
    addProfile(doc, tokens.keys.mkString(" "))

    // Saves document
    if (isNew) topWriter.addDocument(doc)
    else topWriter.updateDocument(new Term(idFldName, id), doc)
    topWriter.commit()
    ()
  }

  /**
    * Adds a profile instance to a personal services document. If there is
    * a profile with the same name, then replace it;
    *
    * @param doc personal services document
    * @param content profile content
    */
  private def addProfile(doc: Document,
                         content: String): Unit = {
    require(doc != null)
    require((content != null) && content.trim.nonEmpty)

    val newContent: String = Tools.strongUniformString(content)
    val oldContent: String = doc.get(contentFldName)

    // Add profile field
    if (oldContent == null) { // new profile
      doc.add(new StoredField(contentFldName, newContent))
    } else { // there was already a profile with the same name
      if (!oldContent.equals(newContent)) {  // same profile but with different sentence
        doc.removeField(contentFldName)  // only one occurrence for profile
        doc.add(new StoredField(contentFldName, newContent))
      }
    }
    // Add similar documents ids
    doc.removeFields(sdIdFldName)
  }

  /**
    * Deletes all profiles from a personal services document
    *
    * @param user personal services document identifier
    */
  def deleteProfiles(user: String): Unit = {
    require((user != null) && user.trim.nonEmpty)

    topWriter.deleteDocuments(new Term(userFldName, user.trim()))
    topWriter.commit()
    ()
  }

  /**
    * Deletes a profile from a personal services document
    *
    * @param user personal services document identifier
    * @param name profile name
    */
  def deleteProfile(user: String,
                    name: String): Unit = {
    require((user != null) && user.trim.nonEmpty)
    require((name != null) && name.trim.nonEmpty)

    val tuser: String = user.trim()
    val tname: String = name.trim()
    val id : String= s"${tuser}_$tname"

    topWriter.deleteDocuments(new Term(idFldName, id))
    topWriter.commit()
    ()
  }

  /**
    * @return a xml document with the users from all personal services documents
    */
  def getUsersXml: String = {
    val head: String = """<?xml version="1.0" encoding="UTF-8"?><users>"""

    getUsers.foldLeft[String](head) {
      case(str, user) => str + s"<user>$user</user>"
    } + "</users>"
  }

  /**
    * @return a collection of users from all personal services documents
    */
  private def getUsers: Set[String] = {
    val docIter: DocumentIterator = new DocumentIterator(DirectoryReader.open(topWriter), Some(Set("user")))

    docIter.foldLeft(Set[String]()) {
      case (set, doc) =>
        Option(doc.get("user")) match {
          case Some(user) => set + user
          case None => set
        }
    }
  }

  /**
    * Given a personal services document, it returns all profiles contents
    * (some fields of that document) represented as a XML document
    *
    * @param user personal services document unique id
    * @return a XML document having profiles names and its contents. Profiles
    *         can have more than one occurrence
    */
  def getProfilesXml(user: String): String = {
    require((user != null) && user.trim.nonEmpty)

    val head: String = """<?xml version="1.0" encoding="UTF-8"?><profiles>"""

    getProfiles(user).foldLeft[String](head) {
      case(str,kv) =>
        val ids: String = kv._2._3.foldLeft("") {
          case (str, id) => s"$str<lucene_id>$id</lucene_id>"
        }
        val dateLong: Long = kv._2._2.toLong
        val updateDate: String = if (dateLong > 0) {
          formatter.format(new Date(dateLong))
        } else "not processed"

        s"$str<profile><name>${kv._1}</name><content>${kv._2._1}</content><update_date>" +
        s"$updateDate</update_date>$ids</profile>"
    } + "</profiles>"
  }

  /**
    * Given a personal services document, it returns all profiles contents
    * (some fields of that document)
    *
    * @param user personal services document unique id
    * @return a collection of profiles: name -> (content, update date, ids). Profiles can not
    *         have more than one occurrence
    */
  private def getProfiles(user: String): Map[String, (String, String, List[String])] = {
    require((user != null) && user.trim.nonEmpty)

    val tUser: String = user.trim()

    getDocuments(userFldName, tUser) match {
      case Some(lst) => lst.foldLeft[Map[String,(String,String,List[String])]] (Map()) {
        case (map, doc) =>
          val name: String = doc.getField(nameFldName).stringValue()
          val content: String = doc.getField(contentFldName).stringValue()
          val updDate: String = doc.getField(updateFldName).stringValue()
          val ids: List[String] = doc.getFields(sdIdFldName).map(_.stringValue()).toList
          map + ((name, (content, updDate, ids)))
      }
      case None => Map()
    }
  }

  /**
    * Given a personal services document described by user id and profile name, it returns a profile contents
    * (some fields of that document)
    *
    * @param user personal services user unique id
    * @param profile desired user profile
    * @return a collection of profiles: (content, update date, ids). Profiles can not
    *         have more than one occurrence
    */
  def getProfile(user: String,
                 profile: String): Option[(String, String, List[String])] = {
    require((user != null) && user.trim.nonEmpty)
    require((profile != null) && profile.trim.nonEmpty)

    getDocuments(Map(userFldName -> user.trim, nameFldName -> profile.trim)).flatMap {
      _.headOption.map {
          doc =>
            val content: String = doc.getField(contentFldName).stringValue()
            val updDate: String = doc.getField(updateFldName).stringValue()
            val ids: List[String] = doc.getFields(sdIdFldName).map(_.stringValue()).toList
            (content, updDate, ids)
      }
    }
  }

  /**
    * Given a id of a personal service document, profiles names and
    * similar documents fields where the profiles will be compared, returns
    * a list of similar documents represented as a XML document
    *
    * @param psId      personal services document id
    * @param profiles  name of profiles used to find similar documents
    * @param outFields fields of similar documents to be retrieved
    * @param maxDocs the maximun number of similar documents to be retrieved
    * @param beginDate filter documents whose 'entrance_date' is younger or equal to beginDate
    * @param sources update only docs whose field 'db' belongs to sources"
    * @param instances update only docs whose field 'instance' belongs to sources"
    * @return an XML document with each desired field and its respective
    *         occurrences, given that fields can have more than one occurrences
    */
  def getSimDocsXml(psId: String,
                    profiles: Set[String],
                    outFields: Set[String],
                    maxDocs: Int = Conf.maxDocs,
                    beginDate: Option[Long],
                    sources: Option[Set[String]] = Conf.sources,
                    instances: Option[Set[String]] = Conf.instances): String = {
    require((psId != null) && psId.trim.nonEmpty)
    require(profiles != null)
    require(outFields != null)

    val simDocs: List[Map[String, List[String]]] =
      getSimDocs(psId, profiles, outFields, maxDocs, beginDate, sources, instances)
    val head: String = s"""<?xml version="1.0" encoding="UTF-8"?><documents total="${simDocs.size}">"""
    simDocs.foldLeft[String] (head) {
      case (str,map) =>
        s"$str<document>" + map.foldLeft[String]("") {
          case (str2, (tag,lst)) =>
            val tag2 = tag.trim().replaceAll(" +", "_")
            val lst2 = if (tag2 equals "decs") {
              lst.map(_.replace("& ", "&amp; "))
            } else lst
            lst2.size match {
              case 0 => str2
              case 1 => str2 + s"<$tag2>${lst2.head}</$tag2>"
              case _ => str2 + lst2.foldLeft[String]("") {
                case (str3,elem) => s"$str3<$tag2>$elem</$tag2>"
              }
            }
        } + "</document>"
    } + "</documents>"
  }

  /**
    * Given a id of a personal service document, profiles names and
    * similar documents fields where the profiles will be compared, returns
    * a list of similar documents
    *
    * @param user personal services document id
    * @param names name of profiles used to find similar documents
    * @param outFlds fields of similar documents to be retrieved
    * @param maxDocs the maximun number of similar documents to be retrieved
    * @param beginDate filter documents whose 'entrance_date' is younger or equal to beginDate
    * @param sources update only docs whose field 'db' belongs to sources"
    * @param instances update only docs whose field 'instance' belongs to instances"
    * @return a list of similar documents, where each similar document is a
    *         a collection of field names and its contents. Each fields can
    *         have more than one occurrence
    */
  def getSimDocs(user: String,
                 names: Set[String],
                 outFlds: Set[String],
                 maxDocs: Int,
                 beginDate: Option[Long],
                 sources: Option[Set[String]],
                 instances: Option[Set[String]]): List[Map[String,List[String]]] = {
    require((user != null) && user.trim.nonEmpty)
    require(names != null)
    require(outFlds != null)
    require(maxDocs > 0)

    val docIds: List[List[Int]] = names.foldLeft[List[List[Int]]](List()) {
      case (lst, name) =>
        val id: String = s"${user.trim}_${name.trim}"
        getDocuments(idFldName, id) match {
          case Some(lst2) =>
            val doc: Document = lst2.head
            val ndoc: Document = if (doc.getField(updateFldName).stringValue().equals("0")) {
                updateSimilarDocs(doc, maxDocs, sources, instances)
            } else doc
            val sdIds: mutable.Seq[IndexableField] = ndoc.getFields().asScala
                                                         .filter(iFld => iFld.name().equals(sdIdFldName))
            if (sdIds.isEmpty) lst
            else lst :+ sdIds.map(_.stringValue().toInt).toList
          case None => lst
        }
    }
    getSimDocs(docIds, outFlds, maxDocs, beginDate)
  }

  /**
    * Given a id of a personal service document, profiles names and similar documents fields where the profiles will be
    * compared, returns a list of similar documents
    *
    * @param docIds document id list for each profile
    * @param outFlds fields of similar documents to be retrieved
    * @param maxDocs the maximun number of similar documents to be retrieved
    * @param beginDate filter documents whose 'entrance_date' is younger or equal to beginDate
    * @return a list of similar documents, where each similar document is a collection of field names and its contents.
    *         Each fields can have more than one occurrence.
    */
  private def getSimDocs(docIds: List[List[Int]],
                         outFlds: Set[String],
                         maxDocs: Int,
                         beginDate: Option[Long]): List[Map[String,List[String]]] = {
    if (docIds.isEmpty) List()
    else {
      val sdDirectory: FSDirectory = FSDirectory.open(Paths.get(simSearch.sdIndexPath))
      val sdReader: DirectoryReader = DirectoryReader.open(sdDirectory)
      val sdSearcher: IndexSearcher = new IndexSearcher(sdReader)
      val oFields: Set[String] = if (outFlds.isEmpty) Conf.idxFldNames ++ Set("db", "update_date") else outFlds

      val list: List[Map[String, List[String]]] = limitDocs(docIds, maxDocs, List()).
        foldLeft[List[Map[String, List[String]]]](List()) {
          case (lst, id) =>
            val fields: Map[String, List[String]] = getDocFields(id, sdSearcher, oFields)
            if (fields.isEmpty) lst else {
              beginDate match {
                case Some(bDate) =>
                  fields.get("update_date") match {
                    case Some(lst2) =>
                      val daysAgo: String = formatter.format(bDate)
                      if (lst2.head.compareTo(daysAgo) >= 0) lst :+ fields
                      else lst
                    case None => lst
                  }
                case None => lst :+ fields
              }
            }
        }
      sdReader.close()
      sdDirectory.close()
      list
    }
  }

  /**
    * Given a set of similar document identifiers for each profile, it
    * takes one id for each profile each time until the desired number of
    * ids has been reached.
    *
    * @param docs list of ids for each profile
    * @param maxDocs the maximum number of ids to be returned
    * @param ids auxiliary id list
    * @return a list of similiar document ids
    */
  @scala.annotation.tailrec
  private def limitDocs(docs: List[List[Int]],
                        maxDocs: Int,
                        ids: List[Int]): List[Int] = {
    require(docs != null)
    require(maxDocs > 0)
    require(ids != null)

    if (docs.isEmpty) ids.take(maxDocs)
    else {
      val num: Int = maxDocs - ids.size
      if (num > 0) {
        val newIds: List[Int] = docs.foldLeft[List[Int]](List()) {
          case (outLst,lstD) => if (lstD.isEmpty) outLst
                                else outLst :+ lstD.head
        }
        val newDocs = docs.foldLeft[List[List[Int]]](List()) {
          case (lst,lstD) => if (lstD.isEmpty||lstD.tail.isEmpty) lst
                            else lst :+ lstD.tail
        }
        limitDocs(newDocs, maxDocs, ids ++ newIds.take(num))
      } else ids.take(maxDocs)
    }
  }

  /**
    * Retrieves the contents of some fields of the 'id' document
    *
    * @param id document identifier (personal service document identifier)
    * @param searcher Lucene IndexSearcher object. See Lucene documentation
    * @param fields set of field names whose content will be retrieved
    * @return a map of field name and it contents. A field can have more than
    *         one occurrence.
    */
  private def getDocFields(id: Int,
                           searcher: IndexSearcher,
                           fields: Set[String]): Map[String,List[String]] = {
    require(id >= 0)
    require(searcher != null)
    require(fields != null)

    if (fields.isEmpty) { // put all fields
      val doc = searcher.storedFields().document(id) // searcher.doc(id)

      doc.getFields().asScala.foldLeft[Map[String,List[String]]] (Map()) {
        case  (map, field) =>
          val name = field.name()
          if ("entrance_date".equals(name)) map
          else {
            val lst = map.getOrElse(name,List[String]())
            map + ((name, field.stringValue() :: lst))
          }
      }
    } else {
      val doc: Document = searcher.storedFields().document(id, fields.asJava) // searcher.doc(id, fields.asJava)

      fields.foldLeft[Map[String,List[String]]] (Map()) {
        case  (map, field) =>
          val flds = doc.getFields(field)
          if (flds.isEmpty) map else {
            val lst = flds.foldLeft[List[String]](List()) {
              case (lst2, fld) => fld.stringValue() :: lst2
            }
            map + ((field, lst))
          }
      }
    }
  }

  /**
    * Retrieves Lucene Document objects given a field name and content
    *
    * @param field document field name
    * @param content document field content
    * @return probably a list of Lucene documents
    */
  def getDocuments(field: String,
                   content: String): Option[List[Document]] = {
    require(field != null)
    require(content != null)

    val topReader: DirectoryReader = DirectoryReader.open(topWriter)
    val topSearcher: IndexSearcher = new IndexSearcher(topReader)
    val query: TermQuery = new TermQuery(new Term(field, content))

    val docs: TopDocs = topSearcher.search(query, Integer.MAX_VALUE)
    // val result = docs.totalHits.value match { Lucene 8.0.0
    val result: Option[List[Document]] = docs.totalHits.value match {
      case 0 => None
      case _ => docs.scoreDocs.foldLeft[Option[List[Document]]] (Some(List[Document]())) {
        case (slst, sdoc) =>
          val doc: Document = topSearcher.storedFields().document(sdoc.doc)
          if (Option(doc.get(idFldName)).getOrElse("").isEmpty) slst
          else slst.map(_ :+ doc)
      }
    }

    topReader.close()
    result
  }

  /**
    * Retrieves Lucene Document objects given a map of (field name -> field content)
    *
    * @param fieldAndContent a map of document (field name -> field content)
    * @return probably a list of Lucene documents
    */
  def getDocuments(fieldAndContent: Map[String,String]): Option[List[Document]] = {
    require(fieldAndContent != null)

    val topReader: DirectoryReader = DirectoryReader.open(topWriter)
    val topSearcher: IndexSearcher = new IndexSearcher(topReader)

    val bbuilder: BooleanQuery.Builder = new BooleanQuery.Builder()
    fieldAndContent.foreach(kv => bbuilder.add(new TermQuery(new Term(kv._1, kv._2)), BooleanClause.Occur.MUST))
    val query: BooleanQuery = bbuilder.build()

    val docs: TopDocs = topSearcher.search(query, Integer.MAX_VALUE)
    // val result = docs.totalHits.value match { Lucene 8.0.0
    val result: Option[List[Document]] = docs.totalHits.value match {
      case 0 => None
      case _ => docs.scoreDocs.foldLeft[Option[List[Document]]] (Some(List[Document]())) {
        case (slst, sdoc) => slst.map(_ :+ topSearcher.storedFields.document(sdoc.doc))  // topSearcher.doc(sdoc.doc))
      }
    }

    topReader.close()
    result
  }

  /**
    * Update sdIdFldName fields of all documents whose update time is outdated
    *
    * @param maxDocs maximum number of similar documents to be retrieved
    * @param sources update only docs whose field 'db' belongs to sources"
    * @param instances update only docs whose field 'instance' belongs to instances"
    * @return the number of updated documents
    */
  def updateAllSimilarDocs(maxDocs: Int,
                           sources: Option[Set[String]],
                           instances: Option[Set[String]]): Int = {
    val updateTime: Long = new Date().getTime
    val query: Query = LongPoint.newRangeQuery(updateFldName, 0, updateTime  - deltaTime) // all documents updated before deltaTime from now
    val topReader: DirectoryReader = DirectoryReader.open(topWriter)
    val topSearcher: IndexSearcher = new IndexSearcher(topReader)
    val topDocs: TopDocs = topSearcher.search(query, Integer.MAX_VALUE)

    //val totalHits = topDocs.totalHits.toInt
    val totalHits: Int = topDocs.totalHits.value.toInt // Lucene 8.0.0
    (0 until totalHits).foreach {
      pos =>
        val scoreDoc: ScoreDoc = topDocs.scoreDocs(pos)
        val doc: Document = topSearcher.storedFields().document(scoreDoc.doc) // topSearcher.doc(scoreDoc.doc)
        updateSimilarDocs(doc, maxDocs, sources, instances, autoCommit = false)
    }

    topReader.close()
    topWriter.commit()
    topWriter.forceMerge(1)

    totalHits
  }

  /**
  * Do an asynchronous update of the sdIdFldName fields of all document whose update time is outdated
    * @param maxDocs maximum number of similar documents to be retrieved
    * @param sources update only docs whose field 'db' belongs to sources
    * @param instances update only docs whose field 'instance' belongs to instances
    */
  def asyncUpdSimilarDocs(maxDocs: Int,
                          sources: Option[Set[String]],
                          instances: Option[Set[String]]): Unit = {
    if (!finishing && !updating) {
      Future {
        updating = true
        while ((!finishing) && updateSimilarDocs(maxDocs, sources, instances).isDefined) {
          println(s"+++ more one document updated! finishing=$finishing")
        }
        updating = false
        println(s"${new Date} - updating was set to false")
      }
    }
    ()
  }

  /**
    * Update sdIdFldName fields of one document whose update time is zero
    *
    * @param maxDocs maximum number of similar documents to be retrieved
    * @param sources update only docs whose field 'db' belongs to sources
    * @param instances update only docs whose field 'instance' belongs to instances
    * @return Some(document) if there was an update otherwise None
    */
  def updateSimilarDocs(maxDocs: Int,
                        sources: Option[Set[String]],
                        instances: Option[Set[String]]): Option[Document] = {
    val query: Query = LongPoint.newExactQuery(updateFldName, 0L) // all documents whose update_date is zero
    val topReader: DirectoryReader = DirectoryReader.open(topWriter)
    val topSearcher: IndexSearcher = new IndexSearcher(topReader)
    val topDocs: TopDocs = topSearcher.search(query, 1)
//println(s"###documentos a serem atualizados:${topDocs.totalHits.value}")

    // Update 'update time' field
    // val retSet = if (topDocs.totalHits.value == 0) None else { Lucene 8.0.0
    val retSet: Option[Document] = if (topDocs.totalHits.value == 0) None else {
      val doc: Document = topSearcher.storedFields().document(topDocs.scoreDocs(0).doc) // topSearcher.doc(topDocs.scoreDocs(0).doc)
      val ndoc: Document = updateSimilarDocs(doc, maxDocs, sources, instances)
      Some(ndoc)
    }
    topReader.close()
    retSet
  }

  /**
    * Update sdIdFldName fields of one document whose update time is outdated
    *
    * @param doc document to be updated
    * @param maxDocs maximum number of similar documents to be retrieved
    * @param sources update only docs whose field 'db' belongs to sources"
    * @param instances update only docs whose field 'instance' belongs to sources"
    * @param autoCommit do a commit after write
    * @param splitTime if true split the period of time to look for similar docs
    * @return document with its similar doc fields updated
    */
  private def updateSimilarDocs(doc: Document,
                                maxDocs: Int,
                                sources: Option[Set[String]],
                                instances: Option[Set[String]],
                                autoCommit: Boolean = true,
                                splitTime: Boolean = true): Document = {
    val updateTime: Long = new Date().getTime
    val ndoc: Document = new Document()

    // Include 'id' field
    val id: String = doc.getField(idFldName).stringValue()
    ndoc.add(new StringField(idFldName, id, Field.Store.YES))

    // Include 'creation time' field
    val ctime: Long = doc.getField(creationFldName).stringValue().toLong
    ndoc.add(new StoredField(creationFldName, ctime))

    // Include 'update time' field
    ndoc.add(new LongPoint(updateFldName, updateTime))
    ndoc.add(new StoredField(updateFldName, updateTime))

    // Include 'user' field
    val user: String = doc.getField(userFldName).stringValue()
    ndoc.add(new StringField(userFldName, user, Field.Store.YES))

    // Include 'prof_name' field
    val pname: String = doc.getField(nameFldName).stringValue()
    ndoc.add(new StoredField(nameFldName, pname))

    // Include 'prof_content' field
    val content: String = doc.getField(contentFldName).stringValue()
    ndoc.add(new StoredField(contentFldName, content))

    // Include 'sd_id' (similar docs) fields
    val docIds: List[Int] = simSearch.search(content, Set[String](), maxDocs, Conf.minNGrams, sources, instances, None, splitTime)
      .map(_._1)
    docIds.foreach(sdId => ndoc.add(new StoredField(sdIdFldName, sdId)))

    // Update document
    topWriter.updateDocument(new Term(idFldName, id), ndoc)
    println(s"+++ ${new Date} - updating document id:$id")
    if (autoCommit) topWriter.commit()

    ndoc
  }

  /**
  * Reset the update_time field from a user's profiles
    * @param user personal services document id
    * @param names name of profiles used to find similar documents
    */
  def resetUpdateTime(user: String,
                      names: Set[String]): Unit = {
    names foreach {
      pname =>
        val id: String = s"${user.trim}_${pname.trim}"
        getDocuments(idFldName, id) foreach {
          docs =>
            val doc: Document = docs.head
            val ndoc: Document = new Document()
            val updateTime: Long = 0L

            // Include 'id' field
            ndoc.add(new StringField(idFldName, id, Field.Store.YES))

            // Include 'creation time' field
            val ctime: Long = doc.getField(creationFldName).stringValue().toLong
            ndoc.add(new StoredField(creationFldName, ctime))

            // Include 'update time' field
            ndoc.add(new LongPoint(updateFldName, updateTime))
            ndoc.add(new StoredField(updateFldName, updateTime))

            // Include 'user' field
            ndoc.add(new StringField(userFldName, user.trim, Field.Store.YES))

            // Include 'prof_name' field
            ndoc.add(new StoredField(nameFldName, pname))

            // Include 'prof_content' field
            val content: String = doc.getField(contentFldName).stringValue()
            ndoc.add(new StoredField(contentFldName, content))

            // Update document
            topWriter.deleteDocuments(new Term(idFldName, id))
            topWriter.addDocument(ndoc)
            topWriter.commit()
        }
    }
  }

  /**
    * Reset the update time field of all documents
    *
    * @return the number of documents whose field "update_time" was set to zero
    */
  def resetAllTimes(): Int = {
    val updateTime: Long = 0
    val query: MatchAllDocsQuery = new MatchAllDocsQuery()

    val topReader: DirectoryReader = DirectoryReader.open(topWriter)
    val topSearcher: IndexSearcher = new IndexSearcher(topReader)
    val topDocs: TopDocs = topSearcher.search(query, Integer.MAX_VALUE)
    val total: Int = topDocs.scoreDocs.length

    // Update 'update time' field
    topDocs.scoreDocs.foreach {
      scoreDoc =>
        val doc: Document = topSearcher.storedFields().document(scoreDoc.doc) // topSearcher.doc(scoreDoc.doc)
        val ndoc: Document = new Document()

        // Include 'id' field
        val id: String = doc.getField(idFldName).stringValue()
        ndoc.add(new StringField(idFldName, id, Field.Store.YES))

        // Include 'creation time' field
        val ctime: Long = doc.getField(creationFldName).stringValue().toLong
        ndoc.add(new StoredField(creationFldName, ctime))

        // Include 'update time' field
        ndoc.add(new LongPoint(updateFldName, updateTime))
        ndoc.add(new StoredField(updateFldName, updateTime))

        // Include 'user' field
        val user: String = doc.getField(userFldName).stringValue()
        ndoc.add(new StringField(userFldName, user, Field.Store.YES))

        // Include 'prof_name' field
        val pname: String = doc.getField(nameFldName).stringValue()
        ndoc.add(new StoredField(nameFldName, pname))

        // Include 'prof_content' field
        val content: String = doc.getField(contentFldName).stringValue()
        ndoc.add(new StoredField(contentFldName, content))

        // Update document
        topWriter.updateDocument(new Term(idFldName, id), ndoc)
    }
    topWriter.commit()
    topReader.close()

    total
  }
}

object TopIndex extends App {
  private def usage(): Unit = {
    Console.err.println("usage: TopIndex" +
      "\n\t-sdIndex=<sdIndexPath> - lucene Index where the similar document will be searched" +
      "\n\t-decsIndex=<decsIndexPath> - lucene Index where DeCS terms present in a document will be found (DeCSHighlighter)" +
      "\n\t-oneWordDecsIndex=<oneWordDecsIndexPath> - lucene Index where DeCS synonyms will be found and used to find similar documents (SimilarDocs)" +
      "\n\t-topIndex=<topIndexPath> - lucene Index where the user profiles are stored" +
      "\n\t-psId=<psId> - personal service identifier" +
      "\n\t-profiles=<prof1>,<prof2>,...,<prof> - user profiles used to search the documents" +
      "\n\t[<-outFields=<field>,<field>,...,<field>] - document fields used will be show in the output" +
      "\n\t[--considerDate] - if present only will return documents newer than x days" +
      "\n\t[--preprocess] - pre process the similar documents to increase speed")

    System.exit(1)
  }

  private def preProcess(topIndex: TopIndex,
                         maxDocs: Int,
                         sources: Option[Set[String]],
                         instances: Option[Set[String]]): Unit = {
    val init: Long = Calendar.getInstance.getTimeInMillis
    print("Resetting all times ...")
    topIndex.resetAllTimes()
    print(" OK\nUpdating all similar docs ...")
    topIndex.updateAllSimilarDocs(maxDocs, sources, instances)
    val end: Long = Calendar.getInstance.getTimeInMillis
    println(" OK")
    println(s"Elapsed time: ${end - init}ms")
  }

  if (args.length < 5) usage()

  val parameters = args.foldLeft[Map[String,String]](Map()) {
    case (map,par) =>
      val split = par.split(" *= *", 2)
      if (split.length == 1) map + ((split(0).substring(2), ""))
      else map + ((split(0).substring(1), split(1)))
  }

  val sdIndexPath: String = parameters("sdIndex")
  val decsIndexPath: String = parameters("decsIndex")
  val oneWordDecsIndex: String = parameters("oneWordDecsIndex")
  val topIndexPath: String = parameters("topIndex")
  val psId: String = parameters("psId")
  val profiles: Set[String] = parameters("profiles").split(" *, *").toSet
  val outFields: Set[String] = parameters.get("outFields") match {
    case Some(sFields) => sFields.split(" *, *").toSet
    case None => Set("id", "ti", "ti_pt", "ti_en", "ti_es", "ab", "ab_pt", "ab_en", "ab_es", "decs", "update_date")//service.Conf.idxFldNames
  }
  val maxDocs: Int = Conf.maxDocs
  val sources: Option[Set[String]] = Conf.sources
  val instances: Option[Set[String]] = Conf.instances
  val considerDate = parameters.contains("considerDate")
  val beginDate: Option[Long] = if (considerDate) {
    Some(Tools.getIahxModificationTime - Tools.daysToTime(Conf.excludeDays + Conf.numDays))
  } else None
  val search: SimDocsSearch = new SimDocsSearch(sdIndexPath, decsIndexPath, oneWordDecsIndex)
  val topIndex: TopIndex = new TopIndex(search, topIndexPath)
  if (parameters.contains("preprocess")) preProcess(topIndex, maxDocs, sources, instances)
  else topIndex.resetUpdateTime(psId, profiles)
  val result: String = topIndex.getSimDocsXml(psId, profiles, outFields, maxDocs, beginDate, sources, instances)
  topIndex.close()

  /*val xml = XML.loadString("<a>Alana<b><c>Beth</c><d>Catie</d></b></a>")
  val formatted = new PrettyPrinter(150, 4).format(xml)
  print(formatted)*/
  println(s"result=$result")
}