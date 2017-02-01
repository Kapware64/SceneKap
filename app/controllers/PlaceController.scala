package controllers

import play.api.mvc._
import play.api.i18n._

import scala.concurrent.ExecutionContext
import javax.inject._

import async._
import db.MongoRepo
import models.Place
import play.api.libs.json.{Json, Writes}

/**
  * Created by NoahKaplan on 10/25/16.
  */
class PlaceController @Inject() (repo: MongoRepo, val messagesApi: MessagesApi)
                                (implicit ec: ExecutionContext) extends Controller with I18nSupport {
  val findNearby = new FindNearby(ec, repo)
  val getDetails = new Details(ec, repo)

  case class Cutoffs(bronze: Int, silver: Int, gold: Int)

  implicit val placeWrites = new Writes[Place] {
    def writes(p: Place) = Json.obj(
      "pid" -> p.pid,
      "rComments" -> Json.parse(p.rComments),
      "tComments" -> Json.parse(p.tComments),
      "website" -> p.website,
      "rPhotoUris" -> p.rPhotoUris,
      "tPhotoUris" -> p.tPhotoUris,
      "summary" -> p.summary,
      "last_summary_mod" -> p.last_summary_mod,
      "extra" -> p.extra
    )
  }

  implicit val cutoffsWrites = new Writes[Cutoffs] {
    def writes(c: Cutoffs) = Json.obj(
      "bronze" -> c.bronze,
      "silver" -> c.silver,
      "gold" -> c.gold
    )
  }

  def index = Action {
    Ok(views.html.index(llForm)(detForm)(changeURLForm)(addPhotoForm)(postCommentForm)(voteCommentForm)(addUserForm)(loginForm)(changeScoreForm)(changePasswordForm)(forgotPasswordForm))
  }

  def nearby(lat: String, long: String) = Action.async {
    findNearby.getNearbyJson(lat, long).map { res =>
      Ok(res)
    } recover {
      case _ => ServiceUnavailable("Database query failed")
    }
  }

  def details(pid: String, placeKeywords: String) = Action.async {
    getDetails.getDetailsJson(pid: String, placeKeywords: String).map { det =>
      Ok(det)
    } recover {
      case _ => ServiceUnavailable("Database query failed")
    }
  }

  def changeWebsite(pid: String, placeKeywords: String, url: String) = Action.async {
    repo.upsertWebsite(placeKeywords, pid, url).map { res =>
      Ok(res.toString)
    } recover {
      case _ => ServiceUnavailable("Database query failed")
    }
  }

  def addPhoto(pid: String, url: String, username: String) = Action.async {
    repo.addPhoto(pid, url, username).map { res =>
      Ok(res.toString)
    } recover {
      case _ => ServiceUnavailable("Database query failed")
    }
  }

  def votePhoto(pid: String, cid: String, voteVal: String, username: String) = Action.async {
    repo.votePhoto(pid, cid, voteVal.toInt, username).map { res =>
      Ok(res.toString)
    } recover {
      case _ => ServiceUnavailable("Database query failed")
    }
  }

  def addComment(pid: String, text: String, username: String) = Action.async {
    repo.addComment(pid, text, username).map { res =>
      Ok(res.toString)
    } recover {
      case _ => ServiceUnavailable("Database query failed")
    }
  }

  def voteComment(pid: String, cid: String, voteVal: String, username: String) = Action.async {
    repo.voteComment(pid, cid, voteVal.toInt, username).map { res =>
      Ok(res.toString)
    } recover {
      case _ => ServiceUnavailable("Database query failed")
    }
  }

  def getAll = Action.async { implicit request =>
    repo.list().map { data =>
      Ok(Json.toJson(data))
    } recover {
      case _ => ServiceUnavailable("Database query failed")
    }
  }

  def nearbyBtn = Action { implicit request =>
    llForm.bindFromRequest.fold(
      errorForm => {
        Ok(views.html.index(errorForm)(detForm)(changeURLForm)(addPhotoForm)(postCommentForm)(voteCommentForm)(addUserForm)(loginForm)(changeScoreForm)(changePasswordForm)(forgotPasswordForm))
      },
      coord => {
        Redirect("/nearby/" + coord.lat + "/" + coord.long)
      }
    )
  }

  def detailsBtn = Action { implicit request =>
    detForm.bindFromRequest.fold(
      errorForm => {
        Ok(views.html.index(llForm)(errorForm)(changeURLForm)(addPhotoForm)(postCommentForm)(voteCommentForm)(addUserForm)(loginForm)(changeScoreForm)(changePasswordForm)(forgotPasswordForm))
      },
      p => {
        Redirect("/details/" + p.pid + "/" + p.placeKeywords)
      }
    )
  }

  def urlBtn = Action.async { implicit request =>
    changeURLForm.bindFromRequest.fold(
      errorForm => {
        repo.list().map { _ =>
          Ok(views.html.index(llForm)(detForm)(errorForm)(addPhotoForm)(postCommentForm)(voteCommentForm)(addUserForm)(loginForm)(changeScoreForm)(changePasswordForm)(forgotPasswordForm))
        } recover {
          case _ => ServiceUnavailable("Database query failed")
        }
      },
      p => {
        repo.upsertWebsite(p.placeKeywords, p.pid, p.url).map { res =>
          Ok(res.toString)
        } recover {
          case _ => ServiceUnavailable("Database query failed")
        }
      }
    )
  }

  def addPhotoBtn = Action.async { implicit request =>
    addPhotoForm.bindFromRequest.fold(
      errorForm => {
        repo.list().map { _ =>
          Ok(views.html.index(llForm)(detForm)(changeURLForm)(errorForm)(postCommentForm)(voteCommentForm)(addUserForm)(loginForm)(changeScoreForm)(changePasswordForm)(forgotPasswordForm))
        } recover {
          case _ => ServiceUnavailable("Database query failed")
        }
      },
      p => {
        repo.addPhoto(p.pid, p.url, p.username).map { res =>
          Ok(res.toString)
        } recover {
          case _ => ServiceUnavailable("Database query failed")
        }
      }
    )
  }

  def addCommentBtn = Action.async { implicit request =>
    postCommentForm.bindFromRequest.fold(
      errorForm => {
        repo.list().map { _ =>
          Ok(views.html.index(llForm)(detForm)(changeURLForm)(addPhotoForm)(errorForm)(voteCommentForm)(addUserForm)(loginForm)(changeScoreForm)(changePasswordForm)(forgotPasswordForm))
        } recover {
          case _ => ServiceUnavailable("Database query failed")
        }
      },
      p => {
        repo.addComment(p.pid, p.text, p.username).map { res =>
          Ok(res.toString)
        } recover {
          case _ => ServiceUnavailable("Database query failed")
        }
      }
    )
  }

  def voteCommentBtn = Action.async { implicit request =>
    voteCommentForm.bindFromRequest.fold(
      errorForm => {
        repo.list().map { _ =>
          Ok(views.html.index(llForm)(detForm)(changeURLForm)(addPhotoForm)(postCommentForm)(errorForm)(addUserForm)(loginForm)(changeScoreForm)(changePasswordForm)(forgotPasswordForm))
        } recover {
          case _ => ServiceUnavailable("Database query failed")
        }
      },
      p => {
        repo.voteComment(p.pid, p.cid, p.voteVal.toInt, p.username).map { res =>
          Ok(res.toString)
        } recover {
          case _ => ServiceUnavailable("Database query failed")
        }
      }
    )
  }

  def votePhotoBtn = Action.async { implicit request =>
    votePhotoForm.bindFromRequest.fold(
      errorForm => {
        repo.list().map { _ =>
          Ok(views.html.index(llForm)(detForm)(changeURLForm)(addPhotoForm)(postCommentForm)(voteCommentForm)(addUserForm)(loginForm)(changeScoreForm)(changePasswordForm)(forgotPasswordForm))
        } recover {
          case _ => ServiceUnavailable("Database query failed")
        }
      },
      p => {
        repo.votePhoto(p.pid, p.cid, p.voteVal.toInt, p.username).map { res =>
          Ok(res.toString)
        } recover {
          case _ => ServiceUnavailable("Database query failed")
        }
      }
    )
  }
}

