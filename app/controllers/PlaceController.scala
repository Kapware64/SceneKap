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

  //TODO: Add recovers to each map. See if they are called appropriately whenever a future is failed

  def index = Action {
    Ok(views.html.index(llForm)(detForm)(changeURLForm)(changePhotoForm)(postCommentForm)(upvoteCommentForm)(downvoteCommentForm))
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

  def addComment(pid: String, text: String) = Action.async {
    repo.addComment(pid, text).map { res =>
      Ok(res.toString)
    }
  }

  def upvoteComment(pid: String, cid: String) = Action.async {
    repo.upvoteComment(pid, cid).map { res =>
      Ok(res.toString)
    }
  }

  def downvoteComment(pid: String, cid: String) = Action.async {
    repo.downvoteComment(pid, cid).map { res =>
      Ok(res.toString)
    }
  }

  def getAll = Action.async { implicit request =>
    repo.list().map { data =>
      Ok(Json.toJson(data))
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

  val postCommentForm: Form[PostCommentForm] = Form {
    mapping(
      "ID" -> nonEmptyText,
      "Text" -> nonEmptyText
    )(PostCommentForm.apply)(PostCommentForm.unapply)
  }

  val upvoteCommentForm: Form[UpvoteCommentForm] = Form {
    mapping(
      "ID" -> nonEmptyText,
      "CID" -> bigDecimal
    )(UpvoteCommentForm.apply)(UpvoteCommentForm.unapply)
  }

  val downvoteCommentForm: Form[DownvoteCommentForm] = Form {
    mapping(
      "ID" -> nonEmptyText,
      "CID" -> bigDecimal
    )(DownvoteCommentForm.apply)(DownvoteCommentForm.unapply)
  }

  def nearbyBtn = Action { implicit request =>
    llForm.bindFromRequest.fold(
      errorForm => {
        Ok(views.html.index(errorForm)(detForm)(changeURLForm)(changePhotoForm)(postCommentForm)(upvoteCommentForm)(downvoteCommentForm))
      },
      coord => {
        Redirect("/nearby/" + coord.lat + "/" + coord.long)
      }
    )
  }

  def detailsBtn = Action { implicit request =>
    detForm.bindFromRequest.fold(
      errorForm => {
        Ok(views.html.index(llForm)(errorForm)(changeURLForm)(changePhotoForm)(postCommentForm)(upvoteCommentForm)(downvoteCommentForm))
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
          Ok(views.html.index(llForm)(detForm)(errorForm)(changePhotoForm)(postCommentForm)(upvoteCommentForm)(downvoteCommentForm))
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
          Ok(views.html.index(llForm)(detForm)(changeURLForm)(errorForm)(postCommentForm)(upvoteCommentForm)(downvoteCommentForm))
        }
      },
      p => {
        repo.upsertPhoto(p.pid, p.url).map { res =>
          Ok(res.toString)
        }
      }
    )
  }

  def addCommentBtn = Action.async { implicit request =>
    postCommentForm.bindFromRequest.fold(
      errorForm => {
        repo.list().map { _ =>
          Ok(views.html.index(llForm)(detForm)(changeURLForm)(changePhotoForm)(errorForm)(upvoteCommentForm)(downvoteCommentForm))
        }
      },
      p => {
        repo.addComment(p.pid, p.text).map { res =>
          Ok(res.toString)
        }
      }
    )
  }

  def upvoteCommentBtn = Action.async { implicit request =>
    upvoteCommentForm.bindFromRequest.fold(
      errorForm => {
        repo.list().map { _ =>
          Ok(views.html.index(llForm)(detForm)(changeURLForm)(changePhotoForm)(postCommentForm)(errorForm)(downvoteCommentForm))
        }
      },
      p => {
        repo.upvoteComment(p.pid, p.cid.toString).map { res =>
          Ok(res.toString)
        }
      }
    )
  }

  def downvoteCommentBtn = Action.async { implicit request =>
    downvoteCommentForm.bindFromRequest.fold(
      errorForm => {
        repo.list().map { _ =>
          Ok(views.html.index(llForm)(detForm)(changeURLForm)(changePhotoForm)(postCommentForm)(upvoteCommentForm)(errorForm))
        }
      },
      p => {
        repo.downvoteComment(p.pid, p.cid.toString).map { res =>
          Ok(res.toString)
        }
      }
    )
  }
}

case class CreateNearbyForm(lat: BigDecimal, long: BigDecimal)
case class GetDetailsForm(pid: String, name: String)
case class ChangeURLForm(pid: String, url: String)
case class ChangePhotoForm(pid: String, url: String)
case class PostCommentForm(pid: String, text: String)
case class UpvoteCommentForm(pid: String, cid: BigDecimal)
case class DownvoteCommentForm(pid: String, cid: BigDecimal)