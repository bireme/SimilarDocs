/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/


package org.bireme.sd

import bruma.master._

import java.io.File
import java.util.regex.Pattern

import org.apache.lucene.document.{Document,Field,StoredField,TextField}
import org.apache.lucene.index.{IndexWriter,IndexWriterConfig}
import org.apache.lucene.store.FSDirectory

//import scala.collection.JavaConverters._
import scala.jdk.CollectionConverters._

/** Creates a Lucene index from a set of xml document files
*
* @author Heitor Barbieri
* date: 20170102
*/
object LuceneIndex extends App {
  private def usage(): Unit = {
    Console.err.println("usage: LuceneIndex" +
      "\n\t<indexPath> - the name+path to the lucene index to be created" +
      "\n\t<decsIndexPath> - the name+path to the lucene decs index to be created" +
      "\n\t<xmlDir> - directory of xml files used to create the index" +
      "\n\t[-xmlFileFilter=<regExp>] - regular expression used to filter xml files" +
      "\n\t[-indexedFields=<field1>[:<boost>],...,<fieldN>[:<boost>]] - xml doc fields to be indexed and stored" +
      "\n\t[-storedFields=<field1>,...,<fieldN>] - xml doc fields to be stored but not indexed" +
      "\n\t[-decs=<dir>] - decs master file directory" +
      "\n\t[-encoding=<str>] - xml file encoding")
    System.exit(1)
  }

  if (args.length < 3) usage()

  val parameters = args.drop(3).foldLeft[Map[String,String]](Map()) {
    case (map,par) =>
      val split = par.split(" *= *", 2)
      map + ((split(0).substring(1), split(1)))
  }

  private val indexPath = args(0)
  private val decsIndexPath = args(1)
  private val xmlDir = args(2)
  private val xmlFileFilter = parameters.getOrElse("xmlFileFilter", ".+\\.xml")
  private val sIdxFields = parameters.getOrElse("indexedFields", "")
  private val fldIdxNames = if (sIdxFields.isEmpty) Set[String]()
                            else sIdxFields.split(" *, *").toSet
  private val sStrdFields = parameters.getOrElse("storedFields", "")
  private val fldStoredNames = (if (sStrdFields.isEmpty) Set[String]()
                                else sStrdFields.split(" *, *").toSet) + "id"
  private val decsDir = parameters.getOrElse("decs", "")
  private val encoding = parameters.getOrElse("encoding", "ISO-8859-1")

  index()

  /**
    * Creates a Lucene index from a collection of xml files
    */
  private def index(): Unit = {
    val matcher = Pattern.compile(xmlFileFilter).matcher("")
    val decsMap = if (decsDir.isEmpty) Map[Int,Set[String]]()
                  else decx2Map()

    val analyzer = new NGramAnalyzer(NGSize.ngram_min_size,
                                     NGSize.ngram_max_size)
    val directory = FSDirectory.open(new File(indexPath).toPath)

    val config = new IndexWriterConfig(analyzer)
    config.setOpenMode(IndexWriterConfig.OpenMode.CREATE)

    val writer = new IndexWriter(directory, config)

    // Creating decs index
    OneWordDecs.createIndex(decsDir, decsIndexPath)

    // Creating similar docs index
    new File(xmlDir).listFiles().sorted.foreach {
      file =>
        if (file.isFile) {
          matcher.reset(file.getName)
          if (matcher.matches)
            indexFile(writer, file.getPath, decsMap)
        }
    }

    print("\nOptimizing index ...")
    writer.forceMerge(1)
    writer.close()
    directory.close()
  }

  /**
    * From a decs master file, converts it into a map of (decs code -> descriptors)
    *
    * @return a map where the keys are the decs code (its mfn) and the values, the
              the descriptors in English, Spanish and Protuguese
    */
  private def decx2Map(): Map[Int,Set[String]] = {
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

  /**
    * Index a single xml file
    *
    * @param indexWriter Lucene index writer
    * @param xmlFile xml file path
    * @param decsMap map of (decs code -> descriptors)
    */
  private def indexFile(indexWriter: IndexWriter,
                        xmlFile: String,
                        decsMap: Map[Int,Set[String]]): Unit = {
    println(s"Indexing file: $xmlFile")

    IahxXmlParser.getElements(xmlFile, encoding, Set()).zipWithIndex.foreach {
      case (map,idx) =>
        if (idx % 5000 == 0) print(".")
        indexWriter.addDocument(map2doc(map.toMap, decsMap))
    }
    println()
  }

  /**
    * Converts a document from a map of fields into a lucene document
    *
    * @param map a document of (field name -> all occurrences of the field)
    * @param decsMap map of (decs code -> descriptors)
    * @return a lucene document
    */
  private def map2doc(map: Map[String,List[String]],
                      decsMap: Map[Int,Set[String]]): Document = {
    val regexp = """\^d\d+""".r
    val doc = new Document()

    map.foreach {
      case (tag,lst) =>
        // Add decs descriptors
        if (decsMap.nonEmpty && (tag == "mj")) {
          lst.foreach {
            fld => regexp.findFirstIn(fld).foreach {
              subd =>
                decsMap.get(subd.substring(2).toInt).foreach {
                  lst2 => lst2.foreach {
                    descr =>
                      val fld = new TextField("decs", descr, Field.Store.YES)
                      doc.add(fld)
                  }
                }
            }
          }
        }
        if (fldIdxNames.contains(tag)) {  // Add indexed fields
          lst.foreach {
            elem => doc.add(new TextField(tag, elem, Field.Store.YES))
          }
        } else if (fldStoredNames.contains(tag)) { // Add stored fields
          lst.foreach {
            elem => doc.add(new StoredField(tag, elem))
          }
        }
    }
    doc
  }
}
