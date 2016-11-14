package async

import db.PlaceRepo
import models.Place

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json._

/**
  * Created by NoahKaplan on 10/25/16.
  */
class FindNearby(ecp: ExecutionContext, repo: PlaceRepo) {
  implicit val ec = ecp

  type NearbyElem = (String, String, String, String, String, String)

  implicit val nearbyElemDetWrites = new Writes[NearbyElem] {
    def writes(e: NearbyElem) = Json.obj(
      "pid" -> e._1,
      "name" -> e._2,
      "lat" -> e._3,
      "long" -> e._4,
      "vicinity" -> e._5,
      "photo_uri" -> e._6
    )
  }

  private def getNearby(lat: String, long: String): Future[List[NearbyElem]] = Future {
    try {
      val raw = get("https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=" + lat + "," + long + "&rankby=distance&key=" + GP_KEY)
      val json: JsValue = Json.parse(raw)

      def extractInfo(a: List[NearbyElem], e: JsValue): List[NearbyElem] = {
        val reqFields: Option[(String, String, String, String, String)] =
          (e \ "name", e \ "place_id", e \ "geometry" \ "location" \ "lat", e \ "geometry" \ "location" \ "lng", e \ "vicinity") match {
            case (JsDefined(JsString(n)), JsDefined(JsString(pid)), JsDefined(JsNumber(l1)), JsDefined(JsNumber(l2)), JsDefined(JsString(v))) =>
              Some((pid, n, l1.toString, l2.toString, v))
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
          case Some((pid, n, l1, l2, v)) =>
            (pid, n, l1, l2, v, photoRef) :: a
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

  def procPhotoUris(l: List[NearbyElem], p: Seq[Place]): List[NearbyElem] = {
    def changeUri(e: NearbyElem, p: Place): NearbyElem = {
      (e._1, e._2, e._3, e._4, e._5, p.photo_uri)
    }

    def helper(acc: List[NearbyElem], l: List[NearbyElem], p: Seq[Place]): List[NearbyElem] = {
      if(l.isEmpty) acc
      else if(p.isEmpty) acc ++ l
      else {
        val (lH, pH) = (l.head, p.head)
        if(lH._1 == pH.pid) helper(changeUri(lH, pH) :: acc, l.tail, p.tail)
        else helper(acc, l.tail, p)
      }
    }

    if(p.isEmpty) l else helper(List[NearbyElem](), l.sortBy(_._1), p.sortBy(_.pid))
  }

  def getNearbyJson(lat: String, long: String): Future[JsValue] = {
    for {
      l <- getNearby(lat, long)
      p <- repo.getMult(l.map(_._1))
    } yield Json.toJson(procPhotoUris(l, p))
  }
}