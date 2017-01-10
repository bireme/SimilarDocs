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

import collection.JavaConverters._

import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.{Files,Path,Paths}
import javax.xml.namespace.QName
import javax.xml.stream.{XMLEventReader,XMLInputFactory,XMLStreamConstants}
import javax.xml.stream.events.{Attribute,EntityReference,StartElement,XMLEvent}

/*
object XMLParser {

  def getElements(xmlFile: String,
                  encoding: String,
                  fldNames: Set[String]): Stream[Map[String,List[String]]] = {
    val factory = XMLInputFactory.newInstance()
    val eventReader = factory.createXMLEventReader(
       	 Files.newBufferedReader(Paths.get(xmlFile), Charset.forName(encoding)))

    openDocument(eventReader)
    getDocs(fldNames.map(_.toLowerCase()), eventReader)
  }

  private def openDocument(eventReader: XMLEventReader): Unit = {
    if (!nextEvent(eventReader).isStartDocument())
      throw new IOException("start document element expected")

    val event = nextEvent(eventReader)
    if (!event.isStartElement())
      throw new IOException("'add' element expected")

    val startElement = event.asStartElement()
    val qName = startElement.getName().getLocalPart().toLowerCase()

    if (!qName.equals("add"))
      throw new IOException("'add' element expected")
  }

  private def getDocs(fldNames: Set[String],
                      eventReader: XMLEventReader):
                                            Stream[Map[String,List[String]]] = {
    getDoc(fldNames, eventReader) match {
      case Some(doc) => doc #:: getDocs(fldNames, eventReader)
      case None => {
        eventReader.close()
        Stream.empty
      }
    }
  }

  private def getDoc(fldNames: Set[String],
                     eventReader: XMLEventReader):
                                           Option[Map[String,List[String]]] = {
    val event = consumeWhites(eventReader)
    event.getEventType() match {
      case XMLStreamConstants.START_ELEMENT =>
        val startElement = event.asStartElement()
        val qName = startElement.getName().getLocalPart().toLowerCase()

        if (qName.equals("doc")) Some(getFields(fldNames, eventReader, Map()))
        else throw new IOException("'doc' element expected")
      case XMLStreamConstants.END_ELEMENT =>
        val endElem = event.asEndElement()

        if (endElem.getName().getLocalPart().equalsIgnoreCase("add")) None
        else throw new IOException("'add' element expected")
      case _ => throw new IOException("'doc' element expected")
    }
  }

  private def getFields(fldNames: Set[String],
                        eventReader: XMLEventReader,
                        auxMap: Map[String,List[String]]):
                                                    Map[String,List[String]] = {
    getField(fldNames, eventReader) match {
      case Some((name,content)) =>
        val map = if (name == null) auxMap else {
          val lst = auxMap.getOrElse(name, List()) :+ content
          auxMap + ((name, lst))
        }
        getFields(fldNames, eventReader, map)
      case None => auxMap
    }
  }

  private def getField(fldNames: Set[String],
                       eventReader: XMLEventReader): Option[(String,String)] = {
    val event = consumeWhites(eventReader)

    event.getEventType() match {
      case XMLStreamConstants.START_ELEMENT =>
        val startElement = event.asStartElement()

        val qName = startElement.getName().getLocalPart().toLowerCase()
        if (!qName.equals("field")) new IOException("'field' element expected")

        getFieldName(fldNames, startElement) match {
          case Some(aname) =>
            val content = getFieldContent(eventReader)
            Some((aname, content))
          case None =>
            getFieldContent(eventReader)
            Some((null, null))
        }
      case XMLStreamConstants.END_ELEMENT =>
        val endElem = event.asEndElement()

        if (endElem.getName().getLocalPart().equalsIgnoreCase("doc")) None
        else throw new IOException("'doc' element expected")
      case _ => throw new IOException("'field' element expected")
    }
  }

  private def getFieldName(fldNames: Set[String],
                           startElement: StartElement): Option[String] = {
    def getFieldName(fldNames: Set[String],
                     attributes: Set[Attribute]): Option[String] = {
      if (attributes.isEmpty) None else {
        val atName = attributes.head.getValue().toLowerCase()

        if (fldNames.isEmpty) Some(atName)
        else fldNames.find(atName.startsWith(_)) match {
          case Some(name) =>  Some(name)
          case None => getFieldName(fldNames, attributes.tail)
        }
      }
    }

    val mSet = scala.collection.mutable.Set[Attribute]()
    val atts = startElement.getAttributes()

    while (atts.hasNext()) mSet.add(atts.next().asInstanceOf[Attribute])
    getFieldName(fldNames, mSet.toSet)
  }

  private def getFieldContent(eventReader: XMLEventReader): String = {
    private def getContent(buffer: String,
                           eventReader: XMLEventReader): String = {
      val event = nextEvent(eventReader)

      event.getEventType() match {
        case XMLStreamConstants.CHARACTERS =>
          getContent(buffer + event.asCharacters().getData(), eventReader)
        case ENTITY_REFERENCE =>
          val data = event.asInstanceOf[EntityReference].getName()
          getContent(buffer + data, eventReader)
        case XMLStreamConstants.END_ELEMENT =>
          val endElem = event.asEndElement()

          if (endElem.getName().getLocalPart().equalsIgnoreCase("field"))
            throw new IOException("'field' element expected")
        case _ => hrow new IOException("characters expected")
      }
    }
    getContent("", eventReader)
  }

  private def consumeWhites(eventReader: XMLEventReader): XMLEvent = {
    val event = nextEvent(eventReader)

    if (event.isCharacters()) nextEvent(eventReader)
    else event
  }

  private def endElement(fldName: String,
                         eventReader: XMLEventReader): Unit = {
    val event = nextEvent(eventReader)

    event.getEventType() match {
      case XMLStreamConstants.END_ELEMENT =>
        val endElem = event.asEndElement()

        if (!endElem.getName().getLocalPart().equalsIgnoreCase(fldName))
             new IOException(s"end element[$fldName] expected")
      case _ => throw new IOException(s"end element[$fldName] expected")
    }
  }

  private def nextEvent(eventReader: XMLEventReader): XMLEvent = {
    if (!eventReader.hasNext()) throw new IOException("premature end of file")

    eventReader.nextEvent()
  }
}
*/
