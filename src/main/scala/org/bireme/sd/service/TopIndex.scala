/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.sd.service

import java.nio.file.Paths
import java.util.{Calendar, Date, GregorianCalendar, TimeZone}

import org.apache.lucene.document._
import org.apache.lucene.index._
import org.apache.lucene.search.{IndexSearcher, MatchAllDocsQuery, TermQuery}
import org.apache.lucene.store.FSDirectory
import org.bireme.sd.{SimDocsSearch, Tools}

import scala.collection.JavaConverters._
import scala.collection.mutable

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
  require((topIndexPath != null) && (!topIndexPath.trim.isEmpty))

  val idFldName = "id"                  // Profile identifier
  val userFldName = "user"              // Personal service user id
  val nameFldName = "prof_name"         // Profile name
  val contentFldName = "prof_content"   // Profile content
  val creationFldName = "creation_time" // Profile creation time
  val updateFldName = "update_time"     // Profile update time
  val sdIdFldName = "sd_id"             // Similar document Lucene doc id

  val lcAnalyzer: LowerCaseAnalyzer = new LowerCaseAnalyzer(true)
  val topDirectory: FSDirectory = FSDirectory.open(Paths.get(topIndexPath))
  val topWriter: IndexWriter =  new IndexWriter(topDirectory,
                                   new IndexWriterConfig(lcAnalyzer).
                                  setOpenMode(IndexWriterConfig.OpenMode.
                                                              CREATE_OR_APPEND))
  topWriter.commit()

  /**
    * Closes all open resources
    */
  def close(): Unit = {
    topWriter.close()
    topDirectory.close()
  }

  /**
    * Adds a profile instance to a personal services document
    *
    * @param user personal services document identifier
    * @param name profile name
    * @param content profile content
    */
  def addProfile(user: String,
                 name: String,
                 content: String): Unit = {
    require((user != null) && (!user.trim.isEmpty))
    require((name != null) && (!name.trim.isEmpty))
    require((content != null) && (!content.trim.isEmpty))

    val tuser = user.trim()
    val tname = name.trim()
    val id = s"${tuser}_$tname"
    val updateTime: Long = new Date().getTime

    // Retrieves or creates the personal service document
    val (doc, isNew) = getDocuments(idFldName, id) match {
      case Some(lst) =>
        val doc2 = lst.head
        doc2.removeField(idFldName)   // Avoid Lucene makes id tokenized (workarround)
        doc2.removeField(userFldName) // Avoid Lucene makes id tokenized (workarround)
        doc2.removeField(updateFldName)
        (doc2, false)
      case None =>
        val doc2 = new Document()
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
    addProfile(doc, content)

    // Saves document
    if (isNew) topWriter.addDocument(doc)
    else topWriter.updateDocument(new Term(idFldName, id), doc)
    topWriter.commit()
  }

  /**
    * Adds a profile instance to a personal services document. If there is
    * a profile with the same name, then replace it;
    *
    * @param doc personal services document
    * @param content profile content
    * @param maxDocs maximum number of similar documents to be retrieved
    */
  private def addProfile(doc: Document,
                         content: String,
                         maxDocs: Int = Conf.maxDocs): Unit = {
    require(doc != null)
    require((content != null) && (!content.trim.isEmpty))

    val newContent: String = Tools.strongUniformString(content, sort = true)
    val oldContent: String = doc.get(contentFldName)

    // Add profile field
    val getSdIds = if (oldContent == null) { // new profile
      doc.add(new StoredField(contentFldName, newContent))
      true
    } else { // there was already a profile with the same name
      if (oldContent.equals(newContent)) false
      else {  // same profile but with different sentence
        doc.removeField(contentFldName)  // only one occurrence for profile
        doc.add(new StoredField(contentFldName, newContent))
        true
      }
    }
    // Add similar documents ids
    if (getSdIds) {
      doc.removeFields(sdIdFldName)
      simSearch.searchIds(newContent, maxDocs, Conf.minNGrams, None).foreach {
        case (id,_) => doc.add(new StoredField(sdIdFldName, id))
      }
    }
  }

  /**
    * Deletes all profiles from a personal services document
    *
    * @param user personal services document identifier
    */
  def deleteProfiles(user: String): Unit = {
    require((user != null) && (!user.trim.isEmpty))

   topWriter.deleteDocuments(new Term(userFldName, user.trim()))
   topWriter.commit()
  }

  /**
    * Deletes a profile from a personal services document
    *
    * @param user personal services document identifier
    * @param name profile name
    */
  def deleteProfile(user: String,
                    name: String): Unit = {
    require((user != null) && (!user.trim.isEmpty))
    require((name != null) && (!name.trim.isEmpty))

    val tuser = user.trim()
    val tname = name.trim()
    val id = s"${tuser}_$tname"

    topWriter.deleteDocuments(new Term(idFldName, id))
    topWriter.commit()
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
    require((user != null) && (!user.trim.isEmpty))

    val head = """<?xml version="1.0" encoding="UTF-8"?><profiles>"""

    getProfiles(user).foldLeft[String](head) {
      case(str,(name,content)) =>
        s"""$str<profile><name>$name</name><content>$content</content></profile>"""
    } + "</profiles>"
  }

  /**
    * Given a personal services document, it returns all profiles contents
    * (some fields of that document)
    *
    * @param user personal services document unique id
    * @return a collection of profiles names and its contents. Profiles can not
    *         have more than one occurrence
    */
  def getProfiles(user: String): Map[String,String] = {
    require((user != null) && (!user.trim.isEmpty))

    val tUser = user.trim()

    getDocuments(userFldName, tUser) match {
      case Some(lst) => lst.foldLeft[Map[String,String]] (Map()) {
        case (map, doc) =>
          val name = doc.getField(nameFldName).stringValue()
          val content = doc.getField(contentFldName).stringValue()
          map + ((name, content))
      }
      case None => Map()
    }
  }

  /**
    * Given a id of a personal service document, profiles names and
    * similar documents fields where the profiles will be compared, returns
    * a list of similar documents represented as a XML document
    *
    * @param psId personal services document id
    * @param profiles name of profiles used to find similar documents
    * @param outFields fields of similar documents to be retrieved
    * @param maxDocs the maximun number of similar documents to be retrieved
    * @param lastDays filter documents whose 'entrance_date' is younger or equal to x days
    * @return an XML document with each desired field and its respective
    *         occurrences, given that fields can have more than one occurrences
    */
  def getSimDocsXml(psId: String,
                    profiles: Set[String],
                    outFields: Set[String],
                    maxDocs: Int,
                    lastDays: Int): String = {
    val days = if (lastDays <= 0) None else Some(lastDays)

    getSimDocsXml(psId, profiles, outFields, maxDocs, days)
  }

  /**
    * Given a id of a personal service document, profiles names and
    * similar documents fields where the profiles will be compared, returns
    * a list of similar documents represented as a XML document
    *
    * @param psId personal services document id
    * @param profiles name of profiles used to find similar documents
    * @param outFields fields of similar documents to be retrieved
    * @param maxDocs the maximun number of similar documents to be retrieved
    * @param lastDays filter documents whose 'entrance_date' is younger or equal to x days
    * @return an XML document with each desired field and its respective
    *         occurrences, given that fields can have more than one occurrences
    */
  def getSimDocsXml(psId: String,
                    profiles: Set[String],
                    outFields: Set[String],
                    maxDocs: Int,
                    lastDays: Option[Int] = None): String = {
    require((psId != null) && (!psId.trim.isEmpty))
    require(profiles != null)
    require(outFields != null)
    require(maxDocs > 0)

    val head = """<?xml version="1.0" encoding="UTF-8"?><documents>"""
    val simDocs = getSimDocs(psId, profiles, outFields, maxDocs, lastDays)
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
    * @param lastDays filter documents whose 'entrance_date' is younger or equal to x days
    * @return a list of similar documents, where each similar document is a
    *         a collection of field names and its contents. Each fields can
    *         have more than one occurrence
    */
  def getSimDocs(user: String,
                 names: Set[String],
                 outFlds: Set[String],
                 maxDocs: Int,
                 lastDays: Option[Int] = None): List[Map[String,List[String]]] = {
    require((user != null) && (!user.trim.isEmpty))
    require(names != null)
    require(outFlds != null)
    require(maxDocs > 0)

    val tuser: String = user.trim()
    val docIds: List[List[Int]] = names.foldLeft[List[List[Int]]](List()) {
      case (lst, name) =>
        val tname = name.trim()
        val id = s"${tuser}_$tname"
        getDocuments(idFldName, id) match {
          case Some(lst2) =>
            val doc: Document = lst2.head
            val ndoc: Document =
              if (doc.getField(updateFldName).stringValue().equals("0")) {
                updateSimilarDocs(doc, maxDocs)
              } else doc
            val sdIds: mutable.Seq[IndexableField] = ndoc.getFields().asScala.filter(iFld => iFld.name().equals(sdIdFldName))
            if (sdIds.isEmpty) lst
            else lst :+ sdIds.map( _.stringValue().toInt).toList
          case None => lst
        }
    }
    if (docIds.isEmpty) List()
    else {
      val sdDirectory = FSDirectory.open(Paths.get(simSearch.sdIndexPath))
      val sdReader = DirectoryReader.open(sdDirectory)
      val sdSearcher = new IndexSearcher(sdReader)
      val list = limitDocs(docIds, maxDocs, lastDays, List()).
                          foldLeft[List[Map[String,List[String]]]](List()) {
        case (lst, id) =>
          val fields = getDocFields(id, sdSearcher, outFlds)
          if (fields.isEmpty) lst else lst :+ fields
      }
      sdReader.close()
      sdDirectory.close()
      list
    }
  }

  /**
    * Given a set of similar document identifiers for each profile, it
    * takes on id for each profile each time until the desired number of
    * ids has been reached.
    *
    * @param docs list of ids for each profile
    * @param maxDocs the maximum number of ids to be returned
    * @param lastDays filter documents whose 'entrance_date' is younger or equal to x days
    * @param ids auxiliary id list
    * @return a list of similiar document ids
    */
  private def limitDocs(docs: List[List[Int]],
                        maxDocs: Int,
                        lastDays: Option[Int],
                        ids: List[Int]): List[Int] = {
    require(docs != null)
    require(maxDocs > 0)
    require(ids != null)

    if (docs.isEmpty) ids.take(maxDocs)
    else {
      val num = maxDocs - ids.size
      if (num > 0) {
        val newIds = lastDays match {
          case Some(days) =>
            val reader = simSearch.getReader
            val searcher = new IndexSearcher(reader)
            val idList = docs.foldLeft[List[Int]](List()) {
              case (outLst,lstD) =>
                if (lstD.isEmpty) outLst
                else if (isNewDoc(lstD.head, searcher, days)) outLst :+ lstD.head
                     else outLst
            }
            reader.close()
            idList
          case None =>
            docs.foldLeft[List[Int]](List()) {
              case (outLst,lstD) => if (lstD.isEmpty) outLst
                                    else outLst :+ lstD.head
            }
        }
        val newDocs = docs.foldLeft[List[List[Int]]](List()) {
          case (lst,lstD) => if (lstD.isEmpty||lstD.tail.isEmpty) lst
                            else lst :+ lstD.tail
        }
        limitDocs(newDocs, maxDocs, lastDays, ids ++ newIds.take(num))
      } else ids.take(maxDocs)
    }
  }

  /**
    * Check if a document is new or not according to its 'entrance_date' flag
    *
    * @param id document identifier (personal service document identifier)
    * @param searcher Lucene IndexSearcher object. See Lucene documentation
    * @param days number of days before today
    * @return true if this document is new or false if not
    */
  private def isNewDoc(id: Int,
                       searcher: IndexSearcher,
                       days: Int): Boolean = {
    require(id >= 0)
    require(searcher != null)
    require (days > 0)

    val now = new GregorianCalendar(TimeZone.getDefault)
    val year = now.get(Calendar.YEAR)
    val month = now.get(Calendar.MONTH)
    val day = now.get(Calendar.DAY_OF_MONTH)
    val todayCal = new GregorianCalendar(year, month, day, 0, 0) // begin of today
    val daysAgoCal = todayCal.clone().asInstanceOf[GregorianCalendar]
    daysAgoCal.add(Calendar.DAY_OF_MONTH, -days)                // begin of x days ago
    val daysAgo = DateTools.dateToString(daysAgoCal.getTime,
                                         DateTools.Resolution.SECOND)
    val field: String = searcher.doc(id).get("entrance_date")

    (field == null) || (field.compareTo(daysAgo) >= 0)
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
      val doc = searcher.doc(id)

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
      val doc = searcher.doc(id, fields.asJava)

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
  private def getDocuments(field: String,
                           content: String): Option[List[Document]] = {
    require(field != null)
    require(content != null)

    val topReader = DirectoryReader.open(topWriter)
    val topSearcher = new IndexSearcher(topReader)
    val query = new TermQuery(new Term(field, content))

//println(s"query=$query")
    val docs = topSearcher.search(query, Integer.MAX_VALUE)
//println(s"totalHits=${docs.totalHits} query=[$query]")
    // val result = docs.totalHits.value match { Lucene 8.0.0
    val result = docs.totalHits match {
      case 0 => None
      case _ => docs.scoreDocs.foldLeft[Option[List[Document]]] (Some(List[Document]())) {
        case (slst, sdoc) => slst.map(_ :+ topSearcher.doc(sdoc.doc))
      }
    }

    topReader.close()
    result
  }

  /**
    * Update sdIdFldName fields of one document whose update time is outdated
    *
    * @param maxDocs maximum number of similar documents to be retrieved
    * @return Some(document) if there was an update otherwise None
    */
  def updateSimilarDocs(maxDocs: Int = Conf.maxDocs): Option[Document] = {
    val updateTime = new Date().getTime
    val deltaTime =  1000 * 60 * 60 * 2 // 2 hours // 8 hours
    val query = LongPoint.newRangeQuery(updateFldName, 0, updateTime  - deltaTime) // all documents updated before deltaTime from now
    val topReader = DirectoryReader.open(topWriter)
    val topSearcher = new IndexSearcher(topReader)
    val topDocs = topSearcher.search(query, 1)
//println(s"###documentos a serem atualizados:${topDocs.totalHits} 0<=x<=${updateTime  - deltaTime}")

    // Update 'update time' field
    // val retSet = if (topDocs.totalHits.value == 0) None else { Lucene 8.0.0
    val retSet = if (topDocs.totalHits == 0) None else {
      val doc = topSearcher.doc(topDocs.scoreDocs(0).doc)
      val ndoc = updateSimilarDocs(doc, maxDocs)
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
    * @return document with its similar doc fields updated
    */
  def updateSimilarDocs(doc: Document,
                        maxDocs: Int): Document = {
    val updateTime = new Date().getTime
    val ndoc = new Document()

    // Include 'id' field
    val id = doc.getField(idFldName).stringValue()
    ndoc.add(new StringField(idFldName, id, Field.Store.YES))

    // Include 'creation time' field
    val ctime = doc.getField(creationFldName).stringValue().toLong
    ndoc.add(new StoredField(creationFldName, ctime))

    // Include 'update time' field
    ndoc.add(new LongPoint(updateFldName, updateTime))
    ndoc.add(new StoredField(updateFldName, updateTime))

    // Include 'user' field
    val user = doc.getField(userFldName).stringValue()
    ndoc.add(new StringField(userFldName, user, Field.Store.YES))

    // Include 'prof_name' field
    val pname = doc.getField(nameFldName).stringValue()
    ndoc.add(new StoredField(nameFldName, pname))

    // Include 'prof_content' field
    val content = doc.getField(contentFldName).stringValue()
    ndoc.add(new StoredField(contentFldName, content))

    // Include 'sd_id' (similar docs) fields
    simSearch.searchIds(content, maxDocs, Conf.minNGrams, None).
      foreach { case (sdId,_) => ndoc.add(new StoredField(sdIdFldName, sdId)) }

    // Update document
    topWriter.updateDocument(new Term(idFldName, id), ndoc)
    topWriter.commit()

    ndoc
  }

  /**
    * Reset the update time field of all documents
    *
    */
  def resetAllTimes(): Unit = {
    val updateTime = 0
    val query = new MatchAllDocsQuery()

    val topReader = DirectoryReader.open(topWriter)
    val topSearcher = new IndexSearcher(topReader)
    val topDocs = topSearcher.search(query, Integer.MAX_VALUE)

    // Update 'update time' field
    topDocs.scoreDocs.foreach {
      scoreDoc =>
        val doc = topSearcher.doc(scoreDoc.doc)
        val ndoc = new Document()

        // Include 'id' field
        val id = doc.getField(idFldName).stringValue()
        ndoc.add(new StringField(idFldName, id, Field.Store.YES))

        // Include 'creation time' field
        val ctime = doc.getField(creationFldName).stringValue().toLong
        ndoc.add(new StoredField(creationFldName, ctime))

        // Include 'update time' field
        ndoc.add(new LongPoint(updateFldName, updateTime))
        ndoc.add(new StoredField(updateFldName, updateTime))

        // Include 'user' field
        val user = doc.getField(userFldName).stringValue()
        ndoc.add(new StringField(userFldName, user, Field.Store.YES))

        // Include 'prof_name' field
        val pname = doc.getField(nameFldName).stringValue()
        ndoc.add(new StoredField(nameFldName, pname))

        // Include 'prof_content' field
        val content = doc.getField(contentFldName).stringValue()
        ndoc.add(new StoredField(contentFldName, content))

        // Update document
        topWriter.updateDocument(new Term(idFldName, id), ndoc)
    }
    topWriter.commit()
    topReader.close()
  }
}

object TopIndex extends App {
  private def usage(): Unit = {
    Console.err.println("usage: TopIndex" +
      "\n\t<sdIndexPath> - lucene Index where the similar document will be searched" +
      "\n\t<decsIndexPath> - lucene Index where the one word decs synonyms document will be searched" +
      "\n\t<topIndexPath> - lucene Index where the user profiles are stored" +
      "\n\t<psId> - personal service identifier" +
      "\n\t-profiles=<prof1>,<prof2>,...,<prof>] - user profiles used to search the documents" +
      "\n\t[<-outFields=<field>,<field>,...,<field>] - document fields used will be show in the output" +
      "\n\t[-maxDocs=<num>] - maximum number of retrieved similar documents" +
      "\n\t[-lastDays=<num>] - return only docs that are younger (entrance_date flag) than 'lastDays' days" +
      "\n\t[--preprocess] - pre process the similar documents to increase speed")

    System.exit(1)
  }

  private def preProcess(topIndex: TopIndex): Unit = {
    topIndex.updateSimilarDocs() match {
      case Some(_) =>
        print(".")
        preProcess(topIndex)
      case None => println()
    }
  }

  if (args.length < 5) usage()

  val parameters = args.drop(5).foldLeft[Map[String,String]](Map()) {
    case (map,par) =>
      val split = par.split(" *= *", 2)
      if (split.length == 1) map + ((split(0).substring(2), ""))
      else map + ((split(0).substring(1), split(1)))
  }

  val profiles: Set[String] = args(4).drop(9).split(" *, *").toSet
  val outFields: Set[String] = parameters.get("outFields") match {
    case Some(sFields) => sFields.split(" *, *").toSet
    case None => Set("ti", "ti_pt", "ti_en", "ti_es", "ab", "ab_pt", "ab_en", "ab_es", "decs")//service.Conf.idxFldNames
  }
  val maxDocs: Int = parameters.getOrElse("maxDocs", "10").toInt
  val lastDays: Int = parameters.getOrElse("lastDays", "0").toInt
  val preprocess: Boolean = parameters.contains("preprocess")
  val search: SimDocsSearch = new SimDocsSearch(args(0), args(1))
  val topIndex = new TopIndex(search, args(2))
  if (preprocess) preProcess(topIndex)

  val xml = topIndex.getSimDocsXml(args(3), profiles, outFields, maxDocs, lastDays)

  println(s"xml=$xml")

  topIndex.close()
}