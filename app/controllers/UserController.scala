package controllers

import play.api.mvc._
import play.api.i18n._

import scala.concurrent.ExecutionContext
import javax.inject._

import com.typesafe.config.ConfigFactory
import db.MongoRepo
import models.User
import play.api.libs.json.{JsValue, Json, Writes}

/**
  * Created by NoahKaplan on 10/25/16.
  */
class UserController @Inject() (repo: MongoRepo, val messagesApi: MessagesApi)
                                (implicit ec: ExecutionContext) extends Controller with I18nSupport {
  val appConf = ConfigFactory.load

  val BRONZE_CUTOFF: Int = appConf.getInt("scoring.bronzeCutoff")
  val SILVER_CUTOFF: Int = appConf.getInt("scoring.silverCutoff")
  val GOLD_CUTOFF: Int = appConf.getInt("scoring.goldCutoff")

  case class Cutoffs(bronze: Int, silver: Int, gold: Int)

  implicit val userWrites = new Writes[User] {
    def writes(u: User) = Json.obj(
      "username" -> u.username,
      "password" -> u.password,
      "email" -> u.email,
      "deviceID" -> u.deviceID,
      "score" -> u.score
    )
  }

  implicit val cutoffsWrites = new Writes[Cutoffs] {
    def writes(c: Cutoffs) = Json.obj(
      "bronze" -> c.bronze,
      "silver" -> c.silver,
      "gold" -> c.gold
    )
  }

  private def getRankCutoffs: JsValue = Json.toJson(Cutoffs(BRONZE_CUTOFF, SILVER_CUTOFF, GOLD_CUTOFF))

  def addUser(username: String, password: String, email: String, deviceID: String) = Action.async {
    repo.addUser(username, password, email, deviceID).map { res =>
      Ok(res.toString)
    } recover {
      case _ =>  ServiceUnavailable("Database query failed")
    }
  }

  def firstLogin(username: String, password: String, deviceID: String) = Action.async {
    repo.firstLogin(username, password, deviceID).map { res =>
      Ok(res.toString)
    } recover {
      case _ =>  ServiceUnavailable("Database query failed")
    }
  }

  def regLogin(username: String, password: String, deviceID: String) = Action.async {
    repo.regLogin(username, password, deviceID).map { res =>
      Ok(res.toString)
    } recover {
      case _ =>  ServiceUnavailable("Database query failed")
    }
  }

  def changeScore(username: String, voteVal: String) = Action.async {
    repo.changeScore(username, voteVal.toInt).map { res =>
      Ok(res.toString)
    } recover {
      case _ =>  ServiceUnavailable("Database query failed")
    }
  }

  def getAllUsers = Action.async { implicit request =>
    repo.getAllUsers.map { data =>
      Ok(Json.toJson(data))
    } recover {
      case _ => ServiceUnavailable("Database query failed")
    }
  }

  def changePassword(username: String, oldPassword: String, newPassword: String) = Action.async { implicit request =>
    repo.changePassword(username, oldPassword, newPassword).map { res =>
      Ok(res.toString)
    } recover {
      case _ => ServiceUnavailable("Database query failed")
    }
  }

  def forgotPassword(username: String, email: String) = Action.async { implicit request =>
    repo.forgotPassword(username, email).map { res =>
      Ok(res.toString)
    } recover {
      case _ => ServiceUnavailable("Database query failed")
    }
  }

  def getCutoffs = Action {
    Ok(getRankCutoffs)
  }

  def addUserBtn = Action.async { implicit request =>
    addUserForm.bindFromRequest.fold(
      errorForm => {
        repo.list().map { _ =>
          Ok(views.html.index(llForm)(detForm)(changeURLForm)(addPhotoForm)(postCommentForm)(voteCommentForm)(errorForm)(loginForm)(changeScoreForm)(changePasswordForm)(forgotPasswordForm))
        } recover {
          case _ => ServiceUnavailable("Database query failed")
        }
      },
      p => {
        repo.addUser(p.username, p.password, p.email, p.deviceID).map { res =>
          Ok(res.toString)
        } recover {
          case _ => ServiceUnavailable("Database query failed")
        }
      }
    )
  }

  def firstLoginBtn = Action.async { implicit request =>
    loginForm.bindFromRequest.fold(
      errorForm => {
        repo.list().map { _ =>
          Ok(views.html.index(llForm)(detForm)(changeURLForm)(addPhotoForm)(postCommentForm)(voteCommentForm)(addUserForm)(errorForm)(changeScoreForm)(changePasswordForm)(forgotPasswordForm))
        } recover {
          case _ => ServiceUnavailable("Database query failed")
        }
      },
      p => {
        repo.firstLogin(p.username, p.password, p.deviceID).map { res =>
          Ok(res.toString)
        } recover {
          case _ => ServiceUnavailable("Database query failed")
        }
      }
    )
  }

  def regLoginBtn = Action.async { implicit request =>
    loginForm.bindFromRequest.fold(
      errorForm => {
        repo.list().map { _ =>
          Ok(views.html.index(llForm)(detForm)(changeURLForm)(addPhotoForm)(postCommentForm)(voteCommentForm)(addUserForm)(errorForm)(changeScoreForm)(changePasswordForm)(forgotPasswordForm))
        } recover {
          case _ => ServiceUnavailable("Database query failed")
        }
      },
      p => {
        repo.regLogin(p.username, p.password, p.deviceID).map { res =>
          Ok(res.toString)
        } recover {
          case _ => ServiceUnavailable("Database query failed")
        }
      }
    )
  }

  def changeScoreBtn = Action.async { implicit request =>
    changeScoreForm.bindFromRequest.fold(
      errorForm => {
        repo.list().map { _ =>
          Ok(views.html.index(llForm)(detForm)(changeURLForm)(addPhotoForm)(postCommentForm)(voteCommentForm)(addUserForm)(loginForm)(errorForm)(changePasswordForm)(forgotPasswordForm))
        } recover {
          case _ => ServiceUnavailable("Database query failed")
        }
      },
      p => {
        repo.changeScore(p.username, p.voteVal.toInt).map { res =>
          Ok(res.toString)
        } recover {
          case _ => ServiceUnavailable("Database query failed")
        }
      }
    )
  }

  def changePasswordBtn = Action.async { implicit request =>
    changePasswordForm.bindFromRequest.fold(
      errorForm => {
        repo.list().map { _ =>
          Ok(views.html.index(llForm)(detForm)(changeURLForm)(addPhotoForm)(postCommentForm)(voteCommentForm)(addUserForm)(loginForm)(changeScoreForm)(errorForm)(forgotPasswordForm))
        } recover {
          case _ => ServiceUnavailable("Database query failed")
        }
      },
      p => {
        repo.changePassword(p.username, p.oldPassword, p.newPassword).map { res =>
          Ok(res.toString)
        } recover {
          case _ => ServiceUnavailable("Database query failed")
        }
      }
    )
  }

  def forgotPasswordBtn = Action.async { implicit request =>
    forgotPasswordForm.bindFromRequest.fold(
      errorForm => {
        repo.list().map { _ =>
          Ok(views.html.index(llForm)(detForm)(changeURLForm)(addPhotoForm)(postCommentForm)(voteCommentForm)(addUserForm)(loginForm)(changeScoreForm)(changePasswordForm)(errorForm))
        } recover {
          case _ => ServiceUnavailable("Database query failed")
        }
      },
      p => {
        repo.forgotPassword(p.username, p.email).map { res =>
          Ok(res.toString)
        } recover {
          case _ => ServiceUnavailable("Database query failed")
        }
      }
    )
  }
}