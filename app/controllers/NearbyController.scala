package controllers

import play.api.mvc._
import play.api.i18n._
import play.api.data.Form
import play.api.data.Forms._

import scala.concurrent.ExecutionContext
import javax.inject._

import async._

/**
  * Created by NoahKaplan on 10/25/16.
  */
class NearbyController @Inject()(val messagesApi: MessagesApi)
                                (implicit ec: ExecutionContext) extends Controller with I18nSupport {
  val findNearby = new FindNearby(ec)

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
    findNearby.getListDet(lat, long).map { res =>
      Ok(views.html.index(inputForm)(res.toString))
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
}

case class CreateNearbyForm(lat: BigDecimal, long: BigDecimal)