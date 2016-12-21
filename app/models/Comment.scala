package models

import play.api.libs.json._

case class Comment(id: Int, poster: String, votes: Int, date: String, text: String)

object Comment {
  implicit val errorFormat = Json.format[Comment]
}