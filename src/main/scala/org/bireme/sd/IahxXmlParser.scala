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

import java.io.IOException
import scala.collection.mutable.Map
import scala.io.{BufferedSource, Source}
import scala.util.{Try, Success, Failure}

object IahxXmlParser {
  val field_regexp = """ *<field name="(.+?)">(.+?)</field> *""".r

  def getElements(xmlFile: String,
                  encoding: String,
                  fldNames: Set[String]): Stream[Map[String,List[String]]] = {
    val map = Map.empty[String,List[String]]

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
      case None => (source,lines)
    }

    docsStream(fldNames.map(_.trim.toLowerCase()), map, source2, lines2)
  }

  private def docsStream(fldNames: Set[String],
                         auxMap: Map[String,List[String]],
                         source: BufferedSource,
                         lines: Iterator[String]):
                                            Stream[Map[String,List[String]]] = {
    getDoc(fldNames, auxMap, lines) match {
      case Some(doc) => doc #:: docsStream(fldNames, auxMap, source, lines)
      case None =>
        source.close()
        Stream.empty
    }
  }

  private def getDoc(fldNames: Set[String],
                     auxMap: Map[String,List[String]],
                     lines: Iterator[String]):
                                            Option[Map[String,List[String]]] = {
    auxMap.clear()

    if (gotoOpenDocTag(lines)) {
      if (lines.isEmpty) None
      else Some(getFields(fldNames, auxMap, lines))
    } else None
  }

  private def getDocEncoding(lines: Iterator[String]): Option[String] = {
    if (lines.hasNext) {
      val line = lines.next().trim()
      if (line.startsWith("<?xml ")) {
        """encoding="([^"]+)""".r.findFirstMatchIn(line) match {
          case Some(mat) => Some(mat.group(1))
          case None => None
        }
      } else if (line.startsWith("<add>")) None
             else getDocEncoding(lines)
    } else None
  }

  private def gotoOpenDocTag(lines: Iterator[String]): Boolean = {
    if (lines.hasNext) {
      val line = lines.next().trim()
      if (line.startsWith("<doc")) true
      else if (line.startsWith("</add>")) false
      else gotoOpenDocTag(lines)
    } else false
  }

  private def getFields(fldNames: Set[String],
                        auxMap: Map[String,List[String]],
                        lines: Iterator[String]): Map[String,List[String]] = {
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
        else fldNames.find(name.startsWith(_)) match {
          case Some(fname) => Some((fname, content)) // matches field name
          case None => Some((null, null))
        }
      case None => None  // found </doc>
    }
  }

  private def getFieldString(lines: Iterator[String]):
                                                     Option[(String,String)] = {
    getOpenFieldElem(lines) match {
      case Some(line) =>
        if (line.contains("</field>")) Some(parseField(line))
        else Some(parseField(getUntilCloseFldElem(line, lines)))
      case None => None
    }
  }

  private def getOpenFieldElem(lines: Iterator[String]): Option[String] = {
    if (!lines.hasNext)
      throw new IOException("<field> or </doc> element expected")

    val line = lines.next()
    if (line.contains("<field")) Some(line)
    else if (line.contains("</doc>")) None
    else getOpenFieldElem(lines)
  }

  private def getUntilCloseFldElem(aux: String,
                                   lines: Iterator[String]): String = {
    if (lines.hasNext) {  // new lines are eliminated
      val line = lines.next()
      if (line.contains("</field>")) aux + " " + line
      else getUntilCloseFldElem(aux + " " + line, lines)
    } else throw new IOException("</field> element expected")
  }

  private def parseField(str: String): (String, String) = {
    Try {
      str match { case field_regexp(name, content) => (name, content) }
    } match {
      case Success((tag,content)) => (tag,content)
      case Failure(_) => parseField1(str)
    }
  }

  private def parseField1(str: String): (String, String) = {
    val pos1 = str.indexOf("<field name=")
    if (pos1 == -1) throw new IOException(s"parseField [$str]")

    val pos2 = str.indexOf(">", pos1 + 12)
    if (pos2 == -1) throw new IOException(s"parseField [$str]")

    val pos3 = str.indexOf("</field>", pos2 + 1)
    if (pos3 == -1) throw new IOException(s"parseField [$str]")

    val name = str.substring(pos1 + 13, pos2 - 1)
    val content = str.substring(pos2 + 1, pos3)

    (name, content)
  }
}
