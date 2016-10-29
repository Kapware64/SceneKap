package async

import de.l3s.boilerpipe.extractors.ArticleSentencesExtractor

import scala.concurrent.{ExecutionContext, Future}
import scala.io
import play.api.libs.json._
import org.htmlcleaner.{HtmlCleaner, TagNode}

import java.net.URL

/**
  * Created by NoahKaplan on 10/25/16.
  */
class FindNearby(ecp: ExecutionContext) {
  implicit val ec = ecp
  private val ABOUT_LINK_KEYWORDS: List[String] = List[String]("About", "about", "ABOUT")
  private val ABOUT_KEYWORDS: List[String] = List[String]("We")
  private val CONNECT_TIMEOUT = 5000
  private val READ_TIMEOUT = 5000
  private val MAX_PHOTO_WIDTH = 400
  private val GP_KEY = "AIzaSyBuZtwpHo3XQpxPOFjALeUgazV_QxZudUU"
  private val GP_KEY_DET = "AIzaSyAls_qyBvY6SG919zH7S3Iy9RMBbfypRgw"

  type NearbyElem = (String, List[String], String, String, String)
  type NearbyElemDet = (String, List[String], String, String, List[String], BigDecimal, String, String, String, String)

  implicit val nearbyElemDetWrites = new Writes[NearbyElemDet] {
    def writes(e: NearbyElemDet) = Json.obj(
      "name" -> e._1,
      "type" -> e._2.head,
      "photo_uri" -> e._3,
      "website" -> e._4,
      "reviews" -> e._5,
      "rating" -> e._6,
      "location" -> e._7,
      "phone" -> e._8,
      "pid" -> e._9,
      "summary" -> e._10
    )
  }

  @throws(classOf[java.io.IOException])
  @throws(classOf[java.net.SocketTimeoutException])
  private def get(url: String, connectTimeout: Int = CONNECT_TIMEOUT, readTimeout: Int = READ_TIMEOUT, requestMethod: String = "GET") = {
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

  private def getNearby(lat: String, long: String): Future[List[NearbyElem]] = Future {
    try {
      val raw = get("https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=" + lat + "," + long + "&rankby=distance&key=" + GP_KEY)
      val json: JsValue = Json.parse(raw)

      def extractInfo(a: List[NearbyElem], e: JsValue): List[NearbyElem] = {
        val reqFields: Option[(String, String, String, List[String])] =
          (e \ "name", e \ "place_id", e \ "types", e \ "geometry" \ "location" \ "lat", e \ "geometry" \ "location" \ "lng") match {
            case (JsDefined(JsString(n)), JsDefined(JsString(pid)), JsDefined(JsArray(t)), JsDefined(JsNumber(l1)), JsDefined(JsNumber(l2))) =>
              Some((n, pid, l1 + "," + l2, t.foldRight(List[String]()) {(e, a) => val JsString(str) = e; str :: a}))
            case _ => None
          }
        val photoRef: String = e \ "photos" match {
            case JsDefined(JsArray(pArr)) =>
              if(pArr.nonEmpty) {
                pArr.head \ "photo_reference" match {
                  case JsDefined(JsString(pr)) => pr
                  case _ => ""
                }
              }
              else ""
            case _ => ""
          }

        reqFields match {
          case Some((n, pid, v, types)) =>
            (n, types, pid, photoRef, v) :: a
          case None => a
        }
      }

      json \ "results" match {
        case JsDefined(results) =>
          val optArr = results.asOpt[JsArray]
          optArr match {
            case Some(arr) => arr.value.foldRight(List[NearbyElem]()) {(e, a) => extractInfo(a, e)}
            case None => List[NearbyElem]()
          }
        case _ => List[NearbyElem]()
      }
    } catch {
      case ioe: java.io.IOException =>  List[NearbyElem]()
      case ste: java.net.SocketTimeoutException => List[NearbyElem]()
    }
  }

  private def getDetails(e: NearbyElem): Future[NearbyElemDet] = Future {
    val photoUri = if(e._4.isEmpty) "" else "https://maps.googleapis.com/maps/api/place/photo?maxwidth=" + MAX_PHOTO_WIDTH +
      "&photoreference=" + e._4 + "&key=" + GP_KEY
    val defaultRet = (e._1, e._2, photoUri, "", List[String](), BigDecimal(-1), e._5, "", e._3, "")

    try {
      val raw = get("https://maps.googleapis.com/maps/api/place/details/json?placeid=" + e._3 + "&key=" + GP_KEY_DET)
      val json: JsValue = Json.parse(raw)

      def extractInfo(a: List[String], e: JsValue): List[String] = {
        (e \ "rating", e \ "text") match {
          case (JsDefined(JsNumber(r)), JsDefined(JsString(s))) => r + " stars. " + s :: a
          case _ => a
        }
      }

      def getReviews(json: JsValue): List[String] = {
        json \ "reviews" match {
          case JsDefined(JsArray(r)) => r.foldRight(List[String]()) {(e, a) => extractInfo(a, e)}
          case _ => List[String]()
        }
      }

      json \ "result" match {
        case JsDefined(result) => (e._1, e._2, photoUri, getStrProp("website", result, ""), getReviews(result), getNumProp("rating", result, -1),
                                   e._5, getStrProp("international_phone_number", result, ""), e._3, "")
        case _ => defaultRet
      }
    } catch {
      case ioe: java.io.IOException =>  defaultRet
      case ste: java.net.SocketTimeoutException => defaultRet
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

  //TODO: Make this based upon occurances of wods in placeName and ABOUT_KEYWORDS
  private def getBetterSummary(placeName: String, sum1: String, sum2:String): String = {
    if(sum2 != "") sum2
    else sum1
  }

  private def getSum(e: NearbyElemDet): Future[NearbyElemDet] = {
    def helper(url: String, pSum: String, base: Boolean): Future[(String, String)] = Future {
      def failRet(base: Boolean) = if(base) ("", "") else (pSum, "")

      if(url.isEmpty) failRet(base)
      else {
        try {
          val raw = get(url)

          if(base) (ArticleSentencesExtractor.INSTANCE.getText(raw), getAboutLink(raw, e._4))
          else (getBetterSummary(e._1, pSum, ArticleSentencesExtractor.INSTANCE.getText(raw)), "")
        } catch {
          case ioe: java.io.IOException => failRet(base)
          case ste: java.net.SocketTimeoutException => failRet(base)
        }
      }
    }

    for {
      (s1, aUrl) <- helper(e._4, "", true)
      (s2, _) <- helper(aUrl, s1, false)
    } yield (e._1, e._2, e._3, e._4, e._5, e._6, e._7, e._8, e._9, s2)
  }

  private def getDetailsL(l: List[NearbyElem]): Future[List[NearbyElemDet]] = {
    val futures = l.foldRight(List[Future[NearbyElemDet]]()) {(e, a) => getDetails(e) :: a}
    Future.sequence(futures)
  }

  private def getSumL(l: List[NearbyElemDet]) = {
    val futures = l.foldRight(List[Future[NearbyElemDet]]()) {(e, a) => getSum(e) :: a}
    Future.sequence(futures)
  }

  def getListDet(lat: String, long: String): Future[JsValue] = {
    for {
      l <- getNearby(lat, long)
      lDet <- getDetailsL(l)
      lSum <- getSumL(lDet)
    } yield Json.toJson(lSum)
  }
}