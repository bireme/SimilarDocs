/*=========================================================================

    XDocumentServer © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/XDocumentServer/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.sd.others

import java.io.InputStream

trait DocumentServer {

  /**
    * List the ids of all pdf documents
    * @return a set having all pdf document ids
    */
  def getDocuments: Set[String]

  /**
    * Retrieve a stored document
    * @param id document identifier
    * @return the original document content (bytes) if it is found or 404 (not found) or 500 (internal server error)
    */
  def getDocument(id: String): Either[Int, InputStream]

  /**
    * Store a new document
    * @param id document identifier
    * @param source the source of the document content
    * @return a http error code. 201(created), 409(conflict) is the id already exists or 500 (internal server error)
    */
  def createDocument(id: String,
                     source: InputStream,
                     info: Option[Map[String, Set[String]]] = None): Int

  /**
    * Store a new document
    * @param id document identifier
    * @param url the location where the document is
    * @param info metadata of the document
    * @return a http error code. 201(created), 409(conflict) is the id already exists or 500 (internal server error)
    */
  def createDocument(id: String,
                     url: String,
                     info: Option[Map[String, Set[String]]]): Int

  /**
    * Replace a stored document if there is some or create a new one otherwise
    * @param id document identifier
    * @param source source of the document content
    * @param info metadata of the document
    * @return a http error code. 201(created) if new , 200(ok) if replaced or 500 (internal server error)
    */
  def replaceDocument(id: String,
                      source: InputStream,
                      info: Option[Map[String, Set[String]]] = None): Int

  /**
    * Replace a stored document if there is some or create a new one otherwise
    * @param id document identifier
    * @param url the location where the document is
    * @param info metadata of the document
    * @return a http error code. 201(created) if new , 200(ok) if replaced or 500 (internal server error)
    */
  def replaceDocument(id: String,
                      url: String,
                      info: Option[Map[String, Set[String]]]): Int

  /**
    * Delete a stored document
    * @param id document identifier
    * @return a http error code. 200 (ok) or 404 (not found) or 500 (internal server error)
    */
  def deleteDocument(id: String): Int

  /**
    * Delete all stored documents
    * @return a http error code. 200 (ok) or or 500 (internal server error)
    */
  def deleteDocuments(): Int

  /**
    * Retrieve metadata of a stored pdf document
    * @param id document identifier
    * @return the document metadata if found or 404 (not found) or 500 (internal server error)
    */
  def getDocumentInfo(id: String): Either[Int, Map[String, Set[String]]]

  /**
    * Create a metadata for the document
    * @param id document identifier (document id from FI Admin)
    * @param source source of the document content
    * @param info other metadata source
    * @return the document metadata
    */
  def createDocumentInfo(id: String,
                         source: Option[InputStream],
                         info: Option[Map[String, Set[String]]]): Map[String, Set[String]]
}