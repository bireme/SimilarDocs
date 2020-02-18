package org.bireme.sd.others

import play.api.libs.json.{JsArray, JsObject, JsString, JsValue, Json}

import scala.util.Try

object InfoImpExp {
  type Info = Map[String, Set[String]]

  /**
   * Converts a map of type "[id, info]" into a string with json format
   *
   * @param map the input map object
   * @return the output string having json content
   */
  def map2Json(map: Map[String, Info]): String = {
    val map1: Map[String, JsObject] = map.map {
      case (k,v) =>
        val map2: Map[String, JsValue] = v.map { case (k2,v2) => k2 -> JsArray(v2.map(JsString).toArray) }
        k -> JsObject(map2)
    }
    Json.stringify(JsObject(map1))
  }

  /**
   * Convert a string having json object(s) into a map of type "[id, info]"
   *
   * @param jsonStr the input string having json content
   * @return the output map object
   */
  def json2Map(jsonStr: String): Option[Map[String, Info]] = {
    Try {
      val json: JsValue = Json.parse(jsonStr)
      val jObj: JsObject = json.asInstanceOf[JsObject]
      val value1: Map[String, JsValue] = jObj.value.toMap

      value1.map {
        case (k,v) =>
          val value2: Map[String, JsValue] = v.asInstanceOf[JsObject].value.toMap
          val info: Info = value2.map {
            case (k2,v2) =>
              val set1: Set[JsValue] = v2.asInstanceOf[JsArray].value.toSet
              val set2: Set[String] = set1.map(_.as[String])
              k2 -> set2
          }
          k -> info
      }
    }.toOption
  }
}
