/*=========================================================================

    Copyright © 2017 BIREME/PAHO/WHO

    This file is part of MarkAbstract.

    MarkAbstract is free software: you can redistribute it and/or
    modify it under the terms of the GNU Lesser General Public License as
    published by the Free Software Foundation, either version 2.1 of
    the License, or (at your option) any later version.

    MarkAbstract is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public
    License along with MarkAbstract. If not, see <http://www.gnu.org/licenses/>.

=========================================================================*/

package org.bireme.sd

import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.{ErrorHandler,InputSource,SAXParseException}

class SimpleErrorHandler extends ErrorHandler {
  var errMsg = ""

  def warning(e: SAXParseException): Unit = {
    errMsg = e.getMessage()
  }

  def error(e: SAXParseException): Unit = {
    errMsg = e.getMessage()
  }

  def fatalError(e: SAXParseException): Unit = {
    errMsg = e.getMessage()
  }

  def getErrMsg: Option[String] = if (errMsg.isEmpty) None else Some(errMsg)
}

class CheckXml {
  def check(xml: String): Option[String] = {
    require (xml != null)

    val factory = DocumentBuilderFactory.newInstance()
    factory.setValidating(false)
    factory.setNamespaceAware(true)

    val handler = new SimpleErrorHandler()
    val builder = factory.newDocumentBuilder()
    builder.setErrorHandler(handler)
    builder.parse(new InputSource(xml))

    handler.getErrMsg
  }
}

object CheckXml extends App {
  private def usage(): Unit = {
    Console.err.println("usage: CheckXml <filename>")
    System.exit(1)
  }

  if (args.size != 1) usage

  (new CheckXml).check(args(0)) match {
    case Some(msg) => println(s"[${args(0)}] ERROR: $msg")
    case None => println(s"[${args(0)}] - OK")
  }
}
