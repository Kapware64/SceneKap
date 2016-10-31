package async

import de.l3s.boilerpipe.extractors.ArticleSentencesExtractor

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json._
import org.htmlcleaner.{HtmlCleaner, TagNode}
import java.net.URL

import db.PlaceRepo
import models.Place

/**
  * Created by NoahKaplan on 10/25/16.
  */
class Summary(ecp: ExecutionContext, repo: PlaceRepo) {
  implicit val ec = ecp

  private def getWebsite(pid: String): Future[String] = {
    def procRes(res: Option[Place]): String = {
      res match {
        case Some(p) => p.website
        case None => ""
      }
    }

    for {
      res <- repo.get(pid)
    } yield procRes(res)
  }

  private def getGoogWebsite(pid: String): Future[String] = Future {
    try {
      val raw = get("https://maps.googleapis.com/maps/api/place/details/json?placeid=" + pid + "&key=" + GP_KEY_DET)
      val json: JsValue = Json.parse(raw)

      json \ "result" \ "website" match {
        case (JsDefined(JsString(s))) => s
        case _ => ""
      }
    } catch {
      case ioe: java.io.IOException =>  ""
      case ste: java.net.SocketTimeoutException => ""
    }
  }

  private def getAboutLink(html: String, url: String): String = {
    val cleaner = new HtmlCleaner
    val elements = cleaner.clean(html).getElementsByName("a", true)

    def loop(l: List[TagNode]): String = {
      if(l.isEmpty) ""
      else if(ABOUT_LINK_KEYWORDS.exists(l.head.getText.toString.contains)) l.head.getAttributeByName("href")
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

  private def getBetterSummary(placeName: String, sum1: String, sum2:String): String = {
    def calcScore(relWords: List[String], textWords: List[String], acc: Int, mult: Int): Int = {
      if(relWords.isEmpty) acc
      else calcScore(relWords.tail, textWords, acc + textWords.count(_.toUpperCase == relWords.head.toUpperCase) * mult, mult)
    }

    if(sum2 == "") sum1
    else if(sum1 == "") sum2
    else {
      val placeWords = placeName.split("\\s+").toList
      val numPlaceWords = placeWords.size
      val score1: Int = calcScore(placeWords, sum1.split("\\s+").toList, 0, 1) + calcScore(ABOUT_KEYWORDS, sum1.split("\\s+").toList, 0, numPlaceWords)
      val score2: Int = calcScore(placeWords, sum2.split("\\s+").toList, 0, 1) + calcScore(ABOUT_KEYWORDS, sum2.split("\\s+").toList, 0, numPlaceWords)

      if(score2 > score1) sum2
      else sum1
    }
  }

  def getSum(pid: String, name: String): Future[String] = {
    def helper(url: String, pSum: String, base: Boolean): Future[(String, String)] = Future {
      def failRet(base: Boolean) = if(base) ("", "") else (pSum, "")

      if(url.isEmpty) failRet(base)
      else {
        try {
          val raw = get(url)

          if(base) (ArticleSentencesExtractor.INSTANCE.getText(raw), getAboutLink(raw, url))
          else (getBetterSummary(name, pSum, ArticleSentencesExtractor.INSTANCE.getText(raw)), "")
        } catch {
          case ioe: java.io.IOException => failRet(base)
          case ste: java.net.SocketTimeoutException => failRet(base)
        }
      }
    }

    for {
      url1 <- getWebsite(pid)
      url2 <- if(url1 == "") getGoogWebsite(pid) else Future {url1}
      (s1, aUrl) <- helper(url2, "", true)
      (s2, _) <- helper(aUrl, s1, false)
    } yield s2
  }
}