package async

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json._

/**
  * Created by NoahKaplan on 10/25/16.
  */
class FindNearby(ecp: ExecutionContext) {
  implicit val ec = ecp

  type NearbyElem = (String, String, String, String, String)

  implicit val nearbyElemDetWrites = new Writes[NearbyElem] {
    def writes(e: NearbyElem) = Json.obj(
      "pid" -> e._1,
      "name" -> e._2,
      "lat" -> e._3,
      "long" -> e._4,
      "photo_uri" -> e._5
    )
  }

  private def getNearby(lat: String, long: String): Future[List[NearbyElem]] = Future {
    try {
      val raw = get("https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=" + lat + "," + long + "&rankby=distance&key=" + GP_KEY)
      val json: JsValue = Json.parse(raw)

      def extractInfo(a: List[NearbyElem], e: JsValue): List[NearbyElem] = {
        val reqFields: Option[(String, String, String, String)] =
          (e \ "name", e \ "place_id", e \ "geometry" \ "location" \ "lat", e \ "geometry" \ "location" \ "lng") match {
            case (JsDefined(JsString(n)), JsDefined(JsString(pid)), JsDefined(JsNumber(l1)), JsDefined(JsNumber(l2))) =>
              Some((pid, n, l1.toString, l2.toString))
            case _ => None
          }
        val photoRef: String = e \ "photos" match {
          case JsDefined(JsArray(pArr)) =>
            if (pArr.nonEmpty) {
              pArr.head \ "photo_reference" match {
                case JsDefined(JsString(pr)) => pr
                case _ => ""
              }
            }
            else ""
          case _ => ""
        }

        reqFields match {
          case Some((pid, n, l1, l2)) =>
            (pid, n, l1, l2, photoRef) :: a
          case None => a
        }
      }

      json \ "results" match {
        case JsDefined(results) =>
          val optArr = results.asOpt[JsArray]
          optArr match {
            case Some(arr) => arr.value.foldRight(List[NearbyElem]()) { (e, a) => extractInfo(a, e) }
            case None => List[NearbyElem]()
          }
        case _ => List[NearbyElem]()
      }
    } catch {
      case ioe: java.io.IOException => List[NearbyElem]()
      case ste: java.net.SocketTimeoutException => List[NearbyElem]()
    }
  }

  def getNearbyJson(lat: String, long: String): Future[JsValue] = {
    for {
      l <- getNearby(lat, long)
    } yield Json.toJson(l)
  }
}