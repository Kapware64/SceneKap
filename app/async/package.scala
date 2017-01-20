import java.net.URL

import scala.collection.JavaConversions._
import com.typesafe.config.ConfigFactory
import de.l3s.boilerpipe.extractors.{ArticleSentencesExtractor, KeepEverythingExtractor}
import org.htmlcleaner.{HtmlCleaner, TagNode}
import play.api.libs.json._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.io

/**
  * Created by NoahKaplan on 10/26/16.
  */
package object async {
  val appConf = ConfigFactory.load

  val ABOUT_LINK_KEYWORDS: Seq[String] = appConf.getStringList("keywords.aboutLink")
  val ABOUT_KEYWORDS: Seq[String] = appConf.getStringList("keywords.aboutDesc")
  val EMPTY_KEYWORDS: Seq[String] = appConf.getStringList("keywords.fillers")
  val CONNECT_TIMEOUT = appConf.getInt("website.connectTimeoutMS")
  val READ_TIMEOUT = appConf.getInt("website.readTimeoutMS")
  val MAX_PHOTO_WIDTH = appConf.getInt("photo.maxWidth")
  val GP_KEY = appConf.getString("googlePlaces.key")
  val GP_KEY_DET = appConf.getString("googlePlaces.detKey")

  def getAboutLink(html: String, url: String): String = {
    val cleaner = new HtmlCleaner
    val elements = cleaner.clean(html).getElementsByName("a", true)

    def loop(l: List[TagNode]): String = {
      if(l.isEmpty) ""
      else if(ABOUT_LINK_KEYWORDS.exists(l.head.getText.toString.toUpperCase.contains)) l.head.getAttributeByName("href")
      else loop(l.tail)
    }

    def correctURL(raw: String, sUrl: String): String = {
      if(raw == "") raw
      else if(!raw.contains("//")) {
        try {
          val url:URL = new URL(sUrl)
          if(raw.charAt(0) == '/') url.getProtocol + "://" + url.getHost + raw
          else url.getProtocol + "://" + url.getHost + "/" + raw
        }
        catch { case e: java.net.MalformedURLException => "" }
      }
      else raw
    }

    correctURL(loop(elements.toList), url)
  }

  def getBetterSummary(placeKeywords: String, sum1: String, sum2:String, about: Boolean = true): (String, Int) = {
    val score1: Int = calcSumScore(placeKeywords, sum1, about)
    val score2: Int = calcSumScore(placeKeywords, sum2, about)

    if(score1 > score2) (sum1, score1)
    else (sum2, score2)
  }

  def calcSumScore(placeKeywords: String, sum: String, about: Boolean): Int = {
    def helper(relWords: Seq[String], textWords: List[String], acc: Int, mult: Int): Int = {
      if(relWords.isEmpty) acc
      else {
        val curWordsUpper = relWords.head.toUpperCase
        helper(relWords.tail, textWords, acc + textWords.count(_.toUpperCase == curWordsUpper) * mult, mult)
      }
    }

    def remEmptyWords(words: List[String], eWords: Seq[String], acc: List[String]): List[String] = {
      if(words.isEmpty) acc
      else if(eWords.contains(words.head.toUpperCase)) remEmptyWords(words.tail, eWords, acc)
      else remEmptyWords(words.tail, eWords, words.head :: acc)
    }

    val placeWords = remEmptyWords(placeKeywords.split("\\s+").toList, EMPTY_KEYWORDS, List[String]())
    val numPlaceWords = placeWords.size
    val aboutScore = if(about) helper(ABOUT_KEYWORDS, sum.split("\\s+").toList, 0, numPlaceWords / 2) else 0
    helper(placeWords, sum.split("\\s+").toList, 0, 1) + aboutScore
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

  def calcUrlSumAndScore(placeKeywords: String, url: String, about: Boolean = true, articleSum: Boolean = true) : Future[(String, Int)] = {
    def helper(url: String, findAbtLink: Boolean): Future[(String, String)] = Future {
      val defaultRet: (String, String) = ("", "")

      if(url.isEmpty) defaultRet
      else {
        try {
          val raw = get(url)
          val sum = if(articleSum) ArticleSentencesExtractor.INSTANCE.getText(raw).replaceAll("\\s+", " ") else KeepEverythingExtractor.INSTANCE.getText(raw).replaceAll("\\s+", " ")
          if(findAbtLink) (sum, getAboutLink(raw, url)) else (sum, "")
        } catch {
          case ioe: java.io.IOException => defaultRet
          case ste: java.net.SocketTimeoutException => defaultRet
        }
      }
    }

    for {
      (s1, aUrl) <- helper(url, true)
      (s2, _) <- helper(aUrl, false)
    } yield getBetterSummary(placeKeywords, s1, s2, about)
  }
}
