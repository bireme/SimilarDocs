package org.bireme.sd

import java.nio.file.Paths
import java.text.Normalizer
import java.text.Normalizer.Form

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.index.{DirectoryReader,Term}
import org.apache.lucene.queryparser.classic.{MultiFieldQueryParser,QueryParser}
import org.apache.lucene.search.{IndexSearcher,TermQuery}
import org.apache.lucene.store.FSDirectory

import scala.collection.immutable.{TreeMap,TreeSet}

class SimilarDocs {

  def search(query: String,
             analyzer: Analyzer,
             searcher: IndexSearcher,
             idxFldName: Set[String],
             freqIdxName: String,
             minMatchWords: Int,
             maxHits: Int): (Set[String],List[Int]) = {
    val separators =
               """[\d\s\,\.\-;\"\=\<\>\$\&\:\+\%\*\@\?\!\~\^\(\)\[\]`'/¿#_\\]"""
    val wds = query.trim.toLowerCase().split(separators).toSet
  //println(s"query=$query\n\n")
  //println(s"strSet=$strSet")
    search(wds, analyzer, searcher, idxFldName, freqIdxName, minMatchWords,
                                                                        maxHits)
  }

  def search(query: Set[String],
             analyzer: Analyzer,
             searcher: IndexSearcher,
             idxFldName: Set[String],
             freqIdxName: String,
             minMatchWords: Int,
             maxHits: Int): (Set[String],List[Int]) = {
  ////println("antes da chamada do getWords")
    val words = getWords(query, 14, freqIdxName)
    val minMatchWds = Math.min(query.size, minMatchWords)
  //println(s"words=$words")
    val parser = new MultiFieldQueryParser(idxFldName.toArray, analyzer)
    val list = words.foldLeft[List[TreeSet[String]]](List()) {
      case (l,key) => l :+ TreeSet(key)
    }
  //println(s"list=$list")
    val in = getExpressions(searcher, parser, list, words, minMatchWds)
    //println(s"in=$in")
    (words,getIds(searcher, parser, in, maxHits, List[Int]()))
  }

  def getDoc(id: Int,
             searcher: IndexSearcher,
             idxFldName: Set[String],
             words: Set[String]): (String, String, String) = {
    val doc = searcher.doc(id)
    val did = doc.get("id")
    val dbname = doc.get("dbname")
    val allWords = idxFldName.foldLeft[List[String]](List()) {
      case (lst,ifname) => {
        val idxFld = doc.get(ifname)
//println(s"idxFld=$idxFld")
        if (idxFld == null) lst else {
          val wds = idxFld.split(
          """[\s\,\.\-;\"\=\<\>\$\&\:\+\%\*\@\?\!\~\^\(\)\[\]`'/¿#_\\]+""")
          .map(w =>if (words.contains(uniformString(w))) s"[[${w.toUpperCase()}]]"
                   else w)
          lst ++ wds
        }
      }
    }
    (dbname, did, allWords.mkString(" "))
  }

  def getDoc2(id: Int,
              searcher: IndexSearcher,
              idxFldName: Set[String],
              words: Set[String]): (Set[String], String,
                                                   Map[String,List[String]]) = {
    val doc = searcher.doc(id)
    val did = id + "/" + doc.get("id")
//println(s"words=$words")
    idxFldName.foldLeft[(Set[String], String, Map[String,List[String]])] (
                                                        TreeSet(), did, Map()) {
      // For each indexed field name
      case ((set, id, map),ifname) => {
        val content = doc.getValues(ifname).toList

        val set2 = content.foldLeft[Set[String]](set) {
          case (s, sentence) => sentence.split(
            """[\s\,\.\-;\"\=\<\>\$\&\:\+\%\*\@\?\!\~\^\(\)\[\]`'/¿#_\\]+""").
            foldLeft[Set[String]](s) {
              case (s,word) =>
                val uniformStr = uniformString(word)
                if (words.contains(uniformStr)) s + uniformStr else s
          }
        }
        val map2 = map + ((ifname, content))
        
        (set2, id, map2)
      }
    }
  }

  private def getIds(searcher: IndexSearcher,
                     parser: QueryParser,
                     in: List[TreeSet[String]],
                     maxHits: Int,
                     found: List[Int]): List[Int] = {
    in match {
      case Nil => found
      case h::t => {
        val ids = getIds(searcher, parser, h.mkString(" AND "), maxHits)
        //val dif = ids -- found
        //println(s"in=$in h=$h expr=${h.mkString(" AND ")} ids=$dif\n")
        val found2 = found ++ (ids -- found)

        if (found2.size < maxHits) getIds(searcher, parser, t, maxHits, found2)
        else found2.take(maxHits)
      }
    }
  }

  private def getIds(searcher: IndexSearcher,
                     parser: QueryParser,
                     expr: String,
                     maxHits: Int): Set[Int] = {
    val qry = parser.parse(expr)

    searcher.search(qry, maxHits).scoreDocs.foldLeft[Set[Int]](Set()) {
      case (set, sc) => set + sc.doc
    }
  }

  private def getWords(wds: Set[String],
                       max: Int,
                       freqIdxName: String): Set[String] = {

    val stopwords = Set("com", "das", "dos", "meu", "por", "que", "nas", "nos",
                        "seu", "sobre", "sua", "teu", "tua", "con", "del",
                        "entre", "las", "los", "para", "por", "about", "and",
                        "for", "her", "his", "its", "like", "more", "the",
                        "objectives", "out", "methods", "results", "conclusion")
    val directory2 = FSDirectory.open(Paths.get(freqIdxName))
    val ireader2 = DirectoryReader.open(directory2)
    val isearcher2 = new IndexSearcher(ireader2)
    val wds2 = wds.map(w => uniformString(w)).filter(w => (w.length >= 3))
    val wds3 = wds2.filterNot(w => stopwords(w))
  //println(s"wds3=${wds3}")
    val words = wds3.foldLeft[Map[Int,Set[String]]](TreeMap()) {
      case (map,key) => getFreq(key, isearcher2) match {
        case Some(qtt) => {
          val kset = map.getOrElse(qtt, Set[String]())
          map + ((qtt,kset + key))
        }
        case None => map
      }
    }
  //println(s"words=$words")
    ireader2.close()
    directory2.close()

    val keys = getWords(words.toSet, Set[String](), max, 0)
  //println(s"keys=$keys")
    keys
  }

  private def getFreq(word: String,
                      is: IndexSearcher): Option[Int] = {
    val qry = new TermQuery(new Term("token", word))
    val top = is.search(qry, 1)
  //println(s"word=$word hits=${top.totalHits}")
    if (top.totalHits == 0) None
    else Some(is.doc(top.scoreDocs(0).doc).get("freq").toInt)
  }

  private def getWords(words: Set[(Int,Set[String])],
                       keys: Set[String],
                       max: Int,
                       size: Int): Set[String] = {
    if (keys.isEmpty)
      if (words.isEmpty) Set()
      else getWords(words.tail, words.head._2, max, size)
    else
      if (size < max) getWords(words, keys.tail, max, size + 1) + keys.head
      else Set()
  }

  private def uniformString(in: String): String = {
    val s = Normalizer.normalize(in.toLowerCase(), Form.NFD)
    s.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
  }

  private def hasHits(searcher: IndexSearcher,
                      parser: QueryParser,
                      expr: String): Boolean = {
    val qry = parser.parse(expr)
    val res = searcher.search(qry, 1).totalHits > 0
  //println(s"bool=$res expr=$qry")
    res
  }

  private def getExpressions(searcher: IndexSearcher,
                              parser: QueryParser,
                              in: List[TreeSet[String]],
                              init: Set[String],
                              minMatchWords: Int): List[TreeSet[String]] = {
  //println(s"\nentrando no getExpressions in=$in")
    val in2 = in.filter(set => hasHits(searcher, parser, set.mkString(" AND ")))
  //println(s"in2=$in2")
    if (in2.isEmpty) List()
    else {
      val size = in2(0).size
      if (size < minMatchWords)
        getExpressions(searcher, parser, addWord(in2, init), init, minMatchWords)
      else getExpressions(searcher, parser, addWord(in2, init), init,
                                                           minMatchWords) ++ in2
    }
  }

  private def addWord(in: List[TreeSet[String]],
                      init: Set[String]): List[TreeSet[String]] = {
    in.foldLeft[List[TreeSet[String]]](List()) {
      case (out, ins) => {
        val last = ins.last
        init.foldLeft[List[TreeSet[String]]](out) {
          case (out2, key) => if (key.compareTo(last) <= 0) out2
                              else out2 :+ (ins + key)
        }
      }
    }
  }
}
