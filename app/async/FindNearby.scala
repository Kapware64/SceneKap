package async

import scala.concurrent.{ExecutionContext, Future}
import scala.io
import play.api.libs.json._

/**
  * Created by NoahKaplan on 10/25/16.
  */
class FindNearby(ecp: ExecutionContext) {
  implicit val ec = ecp
  private val GP_KEY = "AIzaSyBuZtwpHo3XQpxPOFjALeUgazV_QxZudUU"

  type NearbyElem = (String, List[String], String, String, String)
  type NearbyElemDet = (String, List[String], String, String, List[String], Int, String)

  @throws(classOf[java.io.IOException])
  @throws(classOf[java.net.SocketTimeoutException])
  private def get(url: String, connectTimeout: Int = 5000, readTimeout: Int = 5000, requestMethod: String = "GET") = {
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
      val radius = 1000
      val raw = get("https://maps.googleapis.com/maps/api/place/search/json?location=" + lat + "," + long + "&radius=" + radius + "&key=" + GP_KEY)
      val json: JsValue = Json.parse(raw)

      def extractInfo(a: List[NearbyElem], e: JsValue): List[NearbyElem] = {
        val reqFields: Option[(String, String, String, List[String])] =
          (e \ "name", e \ "place_id", e \ "vicinity", e \ "types") match {
            case (JsDefined(JsString(n)), JsDefined(JsString(pid)), JsDefined(JsString(v)), JsDefined(JsArray(a))) =>
              Some((n, pid, v, a.foldRight(List[String]()) {(e, a) => val JsString(str) = e; str :: a}))
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
        case undefined: JsUndefined => List[NearbyElem]()
      }
    } catch {
      case ioe: java.io.IOException =>  List[NearbyElem]()
      case ste: java.net.SocketTimeoutException => List[NearbyElem]()
    }
  }

  private def getDetails(e: NearbyElem): Future[NearbyElemDet] = Future {
    (e._1, e._2, e._4, "", List[String]("Great!"), 5, e._5)
  }

  private def getDetailsL(l: List[NearbyElem]): Future[List[NearbyElemDet]] = {
    val futures = l.foldRight(List[Future[NearbyElemDet]]()) {(e, a) => getDetails(e) :: a}
    Future.sequence(futures)
  }

  //TODO: Get summaries system just like getDetails

  def getListDet(lat: String, long: String) = {
    for {
      l <- getNearby(lat, long)
      lDet <- getDetailsL(l)
      //lDetSum
    } yield lDet
  }
}
