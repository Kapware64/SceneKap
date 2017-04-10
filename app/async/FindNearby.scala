package async

import db.MongoRepo
import models.Place

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json._

/**
  * Created by NoahKaplan on 10/25/16.
  */
class FindNearby(ecp: ExecutionContext, repo: MongoRepo) {
  implicit val ec = ecp

  //pid, name, lat, long, vicinity, photo_uri, viewport
  type NearbyElem = (String, String, String, String, String, String, String)

  implicit val nearbyElemDetWrites = new Writes[NearbyElem] {
    def writes(e: NearbyElem) = Json.obj(
      "pid" -> e._1,
      "name" -> e._2,
      "lat" -> e._3,
      "long" -> e._4,
      "vicinity" -> e._5,
      "photo_uri" -> e._6,
      "viewport" -> e._7
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
                case JsDefined(JsString(pr)) => "https://maps.googleapis.com/maps/api/place/photo?maxwidth=400&photoreference=" + pr + "&key=" + GP_KEY
                case _ => ""
              }
            }
            else ""
          case _ => ""
        }
        val viewportRef: String = (e \ "geometry" \ "viewport" \ "northeast" \ "lat", e \ "geometry" \ "viewport" \ "northeast" \ "lng",
          e \ "geometry" \ "viewport" \ "southwest" \ "lat", e \ "geometry" \ "viewport" \ "southwest" \ "lng") match {
          case (JsDefined(JsNumber(l1)), JsDefined(JsNumber(l2)), JsDefined(JsNumber(l3)), JsDefined(JsNumber(l4))) =>
            l1.toString + ", " + l2.toString + ", " + l3.toString + ", " + l4.toString
          case _ => ""
        }

        reqFields match {
          case Some((pid, n, l1, l2, v)) =>
            (pid, n, l1, l2, v, photoRef, viewportRef) :: a
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

  def procPhotoUris(l: List[NearbyElem], p: List[Place]): List[NearbyElem] = {
    def changeUri(e: NearbyElem, p: Place): NearbyElem = {
      (e._1, e._2, e._3, e._4, e._5, if(p.topPhotoUri.nonEmpty) p.topPhotoUri else e._6, e._7)
    }

    def helper(acc: List[NearbyElem], l: List[NearbyElem], p: List[Place]): List[NearbyElem] = {
      if(l.isEmpty) acc
      else if(p.isEmpty) acc ++ l
      else {
        val (lH, pH) = (l.head, p.head)
        if(lH._1 == pH.pid) helper(changeUri(lH, pH) :: acc, l.tail, p.tail)
        else helper(lH :: acc, l.tail, p)
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