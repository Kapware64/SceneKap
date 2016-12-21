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
class Details(ecp: ExecutionContext, repo: PlaceRepo) {
  implicit val ec = ecp

  type Details = (String, String, String)

  implicit val nearbyElemDetWrites = new Writes[Details] {
    def writes(e: Details) = Json.obj(
      "summary" -> e._1,
      "rComments" -> e._2,
      "tComments" -> e._3
    )
  }

  private def getDBInfo(pid: String): Future[(String, String, String, String, String)] = {
    def procRes(res: Option[Place]): (String, String, String, String, String) = {
      res match {
        case Some(p) => (p.website, p.summary, p.last_summary_mod, p.rComments, p.tComments)
        case None => ("", "", "", "", "")
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

  private def getBetterSummary(placeName: String, sum1: String, sum2:String): String = {
    if(sum2 == "") sum1
    else if(sum1 == "") sum2
    else {
      val score1: Int = calcSumScore(placeName, sum1, true)
      val score2: Int = calcSumScore(placeName, sum2, true)

      if(score2 > score1) sum2
      else sum1
    }
  }

  def setNewSum(summary: String, pid: String): Future[Int] = {
    repo.upsertSummary(pid, summary)
  }

  def getDetailsJson(pid: String, name: String): Future[JsValue] = {
    def helper(url: String, pSum: String, base: Boolean): Future[(String, String)] = Future {
      def failRet(base: Boolean) = if(base) ("", "") else (pSum, "")

      if(url.isEmpty) failRet(base)
      else {
        try {
          val raw = get(url)

          if(base) (ArticleSentencesExtractor.INSTANCE.getText(raw).replaceAll("\\s+", " "), getAboutLink(raw, url))
          else (getBetterSummary(name, pSum, ArticleSentencesExtractor.INSTANCE.getText(raw).replaceAll("\\s+", " ")), "")
        } catch {
          case ioe: java.io.IOException => failRet(base)
          case ste: java.net.SocketTimeoutException => failRet(base)
        }
      }
    }

    for {
      (url1, dbSum, dbSumMod, rComments, tComments) <- getDBInfo(pid)
      url2 <- if(url1 == "") getGoogWebsite(pid) else Future {url1}
      (s1, aUrl) <- if(dbSum == "" || sumModDateExp(dbSumMod)) helper(url2, "", true) else Future{(dbSum, "")}
      (s2, _) <- helper(aUrl, s1, false)
      i <- if(sumModDateExp(dbSumMod)) setNewSum(s2, pid) else Future {0}
    } yield {
      val details: Details = (s2, rComments, tComments)
      Json.toJson(details)
    }
  }
}