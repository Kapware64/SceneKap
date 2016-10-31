package models

import play.api.libs.json._

case class Place(pid: String, rComments: String, tComments: String, website: String, photo_uri: String, extra: String)

object Place {
  implicit val errorFormat = Json.format[Place]
}