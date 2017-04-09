package async

import java.net.URL

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json._
import db.MongoRepo
import models.Place

/**
  * Created by NoahKaplan on 10/25/16.
  */
class Details(ecp: ExecutionContext, repo: MongoRepo) {
  implicit val ec = ecp

  private def getDBInfo(pid: String): Future[(String, String, String, String, String, String, String)] = {
    def procRes(res: Option[Place]): (String, String, String, String, String, String, String) = {
      res match {
        case Some(p) => (p.website, p.summary, p.last_summary_mod, p.rComments, p.tComments, p.rPhotoUris, p.tPhotoUris)
        case None => ("", "", "", "[]", "[]", "[]", "[]")
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
        case (JsDefined(JsString(s))) =>
          val urlObj: URL = new URL(s)
          urlObj.toString
        case _ => ""
      }
    } catch {
      case ioe: java.io.IOException =>  ""
      case ste: java.net.SocketTimeoutException => ""
      case mue: java.net.MalformedURLException => ""
    }
  }

  def setNewSum(summary: String, pid: String): Future[Int] = {
    repo.upsertSummary(pid, summary)
  }

  def getDetailsJson(pid: String, placeKeywords: String): Future[String] = {
    for {
      (url1, dbSum, dbSumMod, rComments, tComments, rPhotoUris, tPhotoUris) <- getDBInfo(pid)
      url2 <- if(url1 == "") getGoogWebsite(pid) else Future {url1}
      (sum, _) <- if(dbSum.isEmpty || (dbSum.nonEmpty && sumModDateExp(dbSumMod))) calcUrlSumAndScore(placeKeywords, url2) else Future{(dbSum, 0)}
      i <- if(sumModDateExp(dbSumMod)) setNewSum(sum, pid) else Future {0}
    } yield "{ " + "\"summary\" : \"" + sum.replace("\"", "\\\"") + "\", \"rComments\" : " + rComments + ", \"tComments\" : " + tComments +
      ", \"rPhotoUris\" : " + rPhotoUris + ", \"tPhotoUris\" : " + tPhotoUris + " }"
  }
}