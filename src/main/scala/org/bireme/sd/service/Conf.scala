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

package org.bireme.sd.service

object Conf {
  val minSim = 0.5f
  //val minSim = 0.0000001f

  val maxDocs: Int = 10

  val idxFldNames = Set("ti",
                        "ti_pt","ti_en","ti_es","ti_it","ti_fr","ti_de","ti_ru",
                        "ab",
                        "ab_pt","ab_en","ab_es","ab_it","ab_fr","ab_de","ab_ru",
                        "ti_eng","ab_french","decs")
}
