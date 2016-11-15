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
  val getDetails = new Details(ec, repo)

  def index = Action {
    Ok(views.html.index(llForm)(detForm)(changeURLForm)(changePhotoForm))
  }

  def nearby(lat: String, long: String) = Action.async {
    findNearby.getNearbyJson(lat, long).map { res =>
      Ok(res)
    }
  }

  def details(pid: String, name: String) = Action.async {
    getDetails.getDetailsJson(pid: String, name: String).map { det =>
      Ok(det)
    }
  }

  def changeWebsite(pid: String, url: String) = Action.async {
    repo.upsertWebsite(pid, url).map { res =>
      Ok(res.toString)
    }
  }

  def changePhoto(pid: String, url: String) = Action.async {
    repo.upsertPhoto(pid, url).map { res =>
      Ok(res.toString)
    }
  }

  val llForm: Form[CreateNearbyForm] = Form {
    mapping(
      "Lat" -> bigDecimal,
      "Long" -> bigDecimal
    )(CreateNearbyForm.apply)(CreateNearbyForm.unapply)
  }

  val detForm: Form[GetDetailsForm] = Form {
    mapping(
      "ID" -> nonEmptyText,
      "Name" -> nonEmptyText
    )(GetDetailsForm.apply)(GetDetailsForm.unapply)
  }

  val changeURLForm: Form[ChangeURLForm] = Form {
    mapping(
      "ID" -> nonEmptyText,
      "URL" -> nonEmptyText
    )(ChangeURLForm.apply)(ChangeURLForm.unapply)
  }

  val changePhotoForm: Form[ChangePhotoForm] = Form {
    mapping(
      "ID" -> nonEmptyText,
      "URL" -> nonEmptyText
    )(ChangePhotoForm.apply)(ChangePhotoForm.unapply)
  }

  def nearbyBtn = Action { implicit request =>
    llForm.bindFromRequest.fold(
      errorForm => {
        Ok(views.html.index(errorForm)(detForm)(changeURLForm)(changePhotoForm))
      },
      coord => {
        Redirect("/nearby/" + coord.lat + "/" + coord.long)
      }
    )
  }

  def detailsBtn = Action { implicit request =>
    detForm.bindFromRequest.fold(
      errorForm => {
        Ok(views.html.index(llForm)(errorForm)(changeURLForm)(changePhotoForm))
      },
      p => {
        Redirect("/details/" + p.pid + "/" + p.name)
      }
    )
  }

  def urlBtn = Action.async { implicit request =>
    changeURLForm.bindFromRequest.fold(
      errorForm => {
        repo.list().map { _ =>
          Ok(views.html.index(llForm)(detForm)(errorForm)(changePhotoForm))
        }
      },
      p => {
        repo.upsertWebsite(p.pid, p.url).map { res =>
          Ok(res.toString)
        }
      }
    )
  }

  def photoBtn = Action.async { implicit request =>
    changePhotoForm.bindFromRequest.fold(
      errorForm => {
        repo.list().map { _ =>
          Ok(views.html.index(llForm)(detForm)(changeURLForm)(errorForm))
        }
      },
      p => {
        repo.upsertPhoto(p.pid, p.url).map { res =>
          Ok(res.toString)
        }
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
case class GetDetailsForm(pid: String, name: String)
case class ChangeURLForm(pid: String, url: String)
case class ChangePhotoForm(pid: String, url: String)