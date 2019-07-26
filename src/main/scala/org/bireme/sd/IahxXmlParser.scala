/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/


package org.bireme.sd

import java.io.IOException

import scala.collection.mutable
import scala.io.{BufferedSource, Source}
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

/** Parses an Iahx XML file to convert it into a Lucene document
  *
  * author: Heitor Barbieri
  * date: 20170110
  */
object IahxXmlParser {
  val field_regexp: Regex = """ *<field name="(.+?)">(.+?)</field> *""".r

  def getElements(xmlFile: String,
                  encoding: String,
                  fldNames: Set[String]): Stream[mutable.Map[String,List[String]]] = {
    val map = mutable.Map.empty[String,List[String]]

    val source = Source.fromFile(xmlFile, encoding)
    val lines = source.getLines()

    val (source2,lines2) = getDocEncoding(lines) match {
      case Some(enc) =>
        if (enc.equals(encoding)) (source,lines)
        else {
          source.close()
          val src = Source.fromFile(xmlFile, enc)
          val lin = src.getLines()
          (src, lin)
        }
      case None =>
        (source,lines)
    }

    docsStream(fldNames.map(_.trim.toLowerCase()), map, source2, lines2)
  }

  private def docsStream(fldNames: Set[String],
                         auxMap: mutable.Map[String,List[String]],
                         source: BufferedSource,
                         lines: Iterator[String]):
                                            Stream[mutable.Map[String,List[String]]] = {
    getDoc(fldNames, auxMap, lines) match {
      case Some(doc) => doc #:: docsStream(fldNames, auxMap, source, lines)
      case None =>
        source.close()
        Stream.empty
    }
  }

  private def getDoc(fldNames: Set[String],
                     auxMap: mutable.Map[String,List[String]],
                     lines: Iterator[String]):
                                            Option[mutable.Map[String,List[String]]] = {
    auxMap.clear()

    if (gotoOpenDocTag(lines)) {
      if (lines.isEmpty) None
      else {
        val flds: mutable.Map[String, List[String]] = getFields(fldNames, auxMap, lines)
        Some(flds)
      }
    } else None
  }

  @scala.annotation.tailrec
  private def getDocEncoding(lines: Iterator[String]): Option[String] = {
    if (lines.hasNext) {
      val line = lines.next().trim()
      if (line.startsWith("<?xml "))
        """encoding="([^"]+)""".r.findFirstMatchIn(line).map(_.group(1))
      else if (line.startsWith("<add>")) None
      else getDocEncoding(lines)
    } else None
  }

  @scala.annotation.tailrec
  private def gotoOpenDocTag(lines: Iterator[String]): Boolean = {
    if (lines.hasNext) {
      val line = lines.next().trim()
      if (line.startsWith("<doc")) true
      else if (line.startsWith("</add>")) false
      else gotoOpenDocTag(lines)
    } else false
  }

  @scala.annotation.tailrec
  private def getFields(fldNames: Set[String],
                        auxMap: mutable.Map[String,List[String]],
                        lines: Iterator[String]): mutable.Map[String,List[String]] = {
    getField(fldNames, lines) match {
      case Some((name,content)) =>
        val map = if (name == null) auxMap else {
          val lst = auxMap.getOrElse(name, List()) :+ content
          auxMap.put(name, lst)
          auxMap
        }
        getFields(fldNames, map, lines)
      case None => auxMap
    }
  }

  private def getField(fldNames: Set[String],
                       lines: Iterator[String]): Option[(String,String)] = {
    getFieldString(lines) match {
      case Some((name, content)) =>
        if (fldNames.isEmpty) Some((name, content))
        else fldNames.find(name.startsWith) match {
          case Some(fname) => Some((fname, content)) // matches field name
          case None => Some((null, null))
        }
      case None => None  // found </doc>
    }
  }

  private def getFieldString(lines: Iterator[String]):
                                                     Option[(String,String)] = {
    getOpenFieldElem(lines).map(line =>
      if (line.contains("/>")) parseField1(line)
      else if (line.contains("</field>")) parseField(line)
      else parseField(getUntilCloseFldElem(line, lines))
    )
  }

  /**
    * Returns the xml lines until there is a 'field' open tag
    *
    * @param lines current xml line and the next ones
    * @return the xml lines until there is a 'field' open tag or None if not
    */
  @scala.annotation.tailrec
  private def getOpenFieldElem(lines: Iterator[String]): Option[String] = {
    if (!lines.hasNext)
      throw new IOException("<field> or </doc> element expected")

    val line = lines.next()
    if (line.contains("<field")) Some(line)
    else if (line.contains("</doc>")) None
    else getOpenFieldElem(lines)
  }

  /**
    * Returns the content of the current xml line until a line having the close
    * tag of 'field' xml element
    *
    * @param aux current xml line
    * @param lines the next xml lines
    * @return the content of the current xml line until a line having the close
    *         tag of 'field' xml element
    */
  @scala.annotation.tailrec
  private def getUntilCloseFldElem(aux: String,
                                   lines: Iterator[String]): String = {
    if (lines.hasNext) {  // new lines are eliminated
      val line = lines.next()
      if (line.contains("</field>")) aux + " " + line
      else getUntilCloseFldElem(aux + " " + line, lines)
    } else throw new IOException("</field> element expected")
  }

  /**
    * Parses the xml using a regular expression and returns the name and
    * content of a xml element 'field'
    *
    * @param str xml content
    * @return name and content of a xml element 'field'
    */
  private def parseField(str: String): (String, String) = {
    Try {
      str match { case field_regexp(name, content) => (name, content) }
    } match {
      case Success((tag,content)) => (tag,content)
      case Failure(_) => parseField1(str)  // To catch rare cases use the other method
    }
  }

  /**
    * Returns the name and content of a xml element 'field'
    *
    * @param str xml content
    * @return name and content of a xml element 'field'
    */
  private def parseField1(str: String): (String, String) = {
    val openPos = str.indexOf("<field name=\"")
    if (openPos == -1) throw new IOException(s"parseField [$str]")

    val endQuotPos = str.indexOf("\"", openPos + 13)
    if (endQuotPos == -1) throw new IOException(s"parseField [$str]")

    val closePos = str.indexOf(">", endQuotPos + 1)
    if (closePos == -1) throw new IOException(s"parseField [$str]")

    val tag = str.substring(openPos + 13, endQuotPos)

    if (str.charAt(closePos - 1) == '/')  // <tag/>
      (tag, "")
    else {                                // <tag>  </tag>
      val closeTagPos = str.indexOf("</field>", closePos + 1)
      if (closeTagPos == -1) throw new IOException(s"parseField [$str]")

      (tag, str.substring(closePos + 1, closeTagPos))
    }
  }
}
