package controllers

import play.api.mvc._
import play.api.i18n._
import play.api.data.Form
import play.api.data.Forms._

import scala.concurrent.ExecutionContext
import javax.inject._

import async._
import db.PlaceRepo
import play.api.libs.json.Json

/**
  * Created by NoahKaplan on 10/25/16.
  */
class PlaceController @Inject() (repo: PlaceRepo, val messagesApi: MessagesApi)
                                 (implicit ec: ExecutionContext) extends Controller with I18nSupport {
  val findNearby = new FindNearby(ec, repo)
  val getSummary = new Summary(ec, repo)

  val inputForm: Form[CreateNearbyForm] = Form {
    mapping(
      "Latitude" -> bigDecimal,
      "Longitude" -> bigDecimal
    )(CreateNearbyForm.apply)(CreateNearbyForm.unapply)
  }

  def index = Action {
    Ok(views.html.index(inputForm)("Hi"))
  }

  def nearby(lat: String, long: String) = Action.async {
    findNearby.getNearbyJson(lat, long).map { res =>
      Ok(res)
    }
  }

  def sum(pid: String, name: String) = Action.async {
    getSummary.getSum(pid: String, name: String).map { sum =>
      Ok(sum)
    }
  }

  def nearbyBtn = Action { implicit request =>
    inputForm.bindFromRequest.fold(
      errorForm => {
        Ok(views.html.index(errorForm)("Failure"))
      },
      coord => {
        Redirect("/nearby/" + coord.lat + "/" + coord.long)
      }
    )
  }

  def getAll = Action.async { implicit request =>
    repo.list().map { data =>
      Ok(Json.toJson(data))
    }
  }
}

case class CreateNearbyForm(lat: BigDecimal, long: BigDecimal)