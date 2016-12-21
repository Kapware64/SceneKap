import de.l3s.boilerpipe.extractors.KeepEverythingExtractor
import org.mongodb.scala.bson.BsonValue
import play.api.libs.json._
import org.mongodb.scala.bson.collection.immutable.Document

import scala.io

/**
  * Created by NoahKaplan on 10/26/16.
  */
package object async {
  val ABOUT_LINK_KEYWORDS: List[String] = List[String]("ABOUT")
  val ABOUT_KEYWORDS: List[String] = List[String]("WE", "OUR", "WAS", "ESTABLISHED", "FOUNDED")
  val EMPTY_KEYWORDS: List[String] = List[String]("AND", "THE")
  val CONNECT_TIMEOUT = 5000
  val READ_TIMEOUT = 5000
  val MAX_PHOTO_WIDTH = 400
  val GP_KEY = "AIzaSyBuZtwpHo3XQpxPOFjALeUgazV_QxZudUU"
  val GP_KEY_DET = "AIzaSyAls_qyBvY6SG919zH7S3Iy9RMBbfypRgw"

  def calcSumScore(placeName: String, sum: String, about: Boolean): Int = {
    def helper(relWords: List[String], textWords: List[String], acc: Int, mult: Int): Int = {
      if(relWords.isEmpty) acc
      else {
        val curWordsUpper = relWords.head.toUpperCase
        helper(relWords.tail, textWords, acc + textWords.count(_.toUpperCase == curWordsUpper) * mult, mult)
      }
    }

    def remEmptyWords(words: List[String], eWords: List[String], acc: List[String]): List[String] = {
      if(words.isEmpty) acc
      else if(eWords.contains(words.head.toUpperCase)) remEmptyWords(words.tail, eWords, acc)
      else remEmptyWords(words.tail, eWords, words.head :: acc)
    }

    val placeWords = remEmptyWords(placeName.split("\\s+").toList, EMPTY_KEYWORDS, List[String]())
    val numPlaceWords = placeWords.size
    val aboutScore = if(about) helper(ABOUT_KEYWORDS, sum.split("\\s+").toList, 0, numPlaceWords / 2) else 0
    helper(placeWords, sum.split("\\s+").toList, 0, 1) + aboutScore
  }

  def calcUrlScore(placeName: String, url: String): Int = {
    if(url.isEmpty) 0
    else {
      try {
        val raw = get(url)
        calcSumScore(placeName, KeepEverythingExtractor.INSTANCE.getText(raw), false)
      } catch {
        case ioe: java.io.IOException => 0
        case ste: java.net.SocketTimeoutException => 0
      }
    }
  }

  //[modDate] is seconds since 1990
  def sumModDateExp(modDateStr: String): Boolean = {
    val curDate: Long = System.currentTimeMillis / 1000
    val modDate = if(modDateStr.length > 0) modDateStr.toLong else 0
    if(curDate - modDate > 2592000) true
    else false
  }

  def getStrProp(str: String, json: JsValue, default: String): String = {
    json \ str match {
      case JsDefined(JsString(prop)) => prop
      case _ => default
    }
  }

  def getNumProp(str: String, json: JsValue, default: BigDecimal): BigDecimal = {
    json \ str match {
      case JsDefined(JsNumber(prop)) => prop
      case _ => default
    }
  }

  @throws(classOf[java.io.IOException])
  @throws(classOf[java.net.SocketTimeoutException])
  def get(url: String, connectTimeout: Int = CONNECT_TIMEOUT, readTimeout: Int = READ_TIMEOUT, requestMethod: String = "GET") = {
    import java.net.{URL, HttpURLConnection}
    val connection = new URL(url).openConnection.asInstanceOf[HttpURLConnection]
    connection.setConnectTimeout(connectTimeout)
    connection.setReadTimeout(readTimeout)
    connection.setRequestMethod(requestMethod)
    val inputStream = connection.getInputStream
    val content = io.Source.fromInputStream(inputStream).mkString
    if (inputStream != null) inputStream.close()
    content
  }
}
