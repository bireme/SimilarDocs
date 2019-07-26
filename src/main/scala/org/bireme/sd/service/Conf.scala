/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/

package org.bireme.sd.service

object Conf {
  val minSim = 0.5f
  //val minSim = 0.0000001f

  val minNGrams = 2  // Minimum number of common ngrams retrieved to consider returning a document

  val maxDocs: Int = 10 // Maximum number of documents to be pre-processed or retrieved

  val lastDays: Option[Int] = Some(7)  // Update only docs that are younger (entrance_date flag) than 'lastDays' days"

  val sources: Option[Set[String]] = Some(Set("MEDLINE", "LILACS", "LIS", "colecionaSUS")) // Update only docs whose field 'db' belongs to sources"

  val idxFldNames: Set[String] = Set("id", "ti",
                                     "ti_pt","ti_en","ti_es","ti_it","ti_fr","ti_de","ti_ru",
                                     "ab",
                                     "ab_pt","ab_en","ab_es","ab_it","ab_fr","ab_de","ab_ru",
                                     "ti_eng","ab_french","decs", "db", "update_date")

  val indexedField: String = "_indexed_" // Unique field which contains all document and that will be indexed
}
