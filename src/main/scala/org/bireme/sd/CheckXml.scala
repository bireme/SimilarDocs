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
