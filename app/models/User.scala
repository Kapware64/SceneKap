package models

import play.api.libs.json._

case class User(username: String, password: String, email: String, deviceID: String, score: Int)

object User {
  implicit val errorFormat = Json.format[User]
}