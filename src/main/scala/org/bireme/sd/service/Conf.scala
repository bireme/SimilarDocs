/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.sd.service

object Conf {
  val minSim = 0.5f
  //val minSim = 0.0000001f

  val minNGrams: Int = 2  // Minimum number of common ngrams retrieved to consider returning a document

  val maxDocs: Int = 10 // Maximum number of documents to be pre-processed or retrieved

  // endDate = iahxLastModificationTime - excludeDays
  // beginDate = endDate - numDays
  val excludeDays: Int = 7 // Number of days before lastModificationTime to calculate the endDate
  val numDays: Int = 10  // Number of days before the endDate to calculate the beginDate

  val sources: Option[Set[String]] = Some(Set("MEDLINE", "LILACS", "LIS", "colecionaSUS")) // Update only docs whose field 'db' belongs to sources"

  val instances: Option[Set[String]] = None // Update only docs whose field 'instance' belongs to instances"

  val idxFldNames: Set[String] = Set("id", "ti",
                                     "ti_pt","ti_en","ti_es","ti_it","ti_fr","ti_de","ti_ru",
                                     /*"ab",
                                     "ab_pt","ab_en","ab_es","ab_it","ab_fr","ab_de","ab_ru","ab_french",*/
                                     "ti_eng", "decs" /*, "db", "update_date"*/ )

  val indexedField: String = "_indexed_" // Unique field which contains all document and that will be indexed
}
