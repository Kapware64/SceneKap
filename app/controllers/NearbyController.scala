package controllers

import scala.concurrent.Future

import play.api.mvc._
import play.api.i18n._
import play.api.data.Form
import play.api.data.Forms._

import scala.concurrent.ExecutionContext
import javax.inject._

class NearbyController @Inject()(val messagesApi: MessagesApi)
                                (implicit ec: ExecutionContext) extends Controller with I18nSupport {
  val testFuture : Future[String] = Future {
    "hi"
  }

  val inputForm: Form[CreateNearbyForm] = Form {
    mapping(
      "Latitude" -> bigDecimal,
      "Longitude" -> bigDecimal
    )(CreateNearbyForm.apply)(CreateNearbyForm.unapply)
  }

  def index = Action.async {
    testFuture.map { res =>
      Ok(views.html.index(inputForm)(res))
    }
  }

  def nearby(lat: String, long: String) = Action.async {
    testFuture.map { res =>
      Ok(views.html.index(inputForm)(BigDecimal(lat) + " " + BigDecimal(long)))
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