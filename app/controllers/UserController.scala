package controllers

import play.api.mvc._
import play.api.i18n._

import scala.concurrent.{ExecutionContext, Future}
import javax.inject._

import com.typesafe.config.ConfigFactory
import db.MongoRepo
import models.User
import play.api.libs.json.{JsValue, Json, Writes}

import scala.util.matching.Regex

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

  def resetPassword(key: String) = Action {
    Ok(views.html.cpass(resetPasswordForm)("")(key))
  }

  def getCutoffs = Action {
    Ok(getRankCutoffs)
  }

  def addUserBtn = Action.async { implicit request =>
    addUserForm.bindFromRequest.fold(
      errorForm => {
        Future {
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
        Future {
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
        Future {
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
        Future {
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
        Future {
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
        Future {
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

  def resetPasswordBtn(key: String) = Action.async { implicit request =>
    resetPasswordForm.bindFromRequest.fold(
      errorForm => {
        Future{Ok(views.html.cpass(resetPasswordForm)("There was an unexpected error. Please try again.")(key))}
      },
      p => {
        def isAllDigits(x: String) = x forall Character.isDigit

        val (username, newPassword) = (p.username, p.newPassword)
        val numPattern: Regex = "[0-9]+".r
        val containsNumOpt: Option[String] = numPattern.findFirstIn(newPassword)
        val containsNumBool = containsNumOpt match {
          case Some(_) => true
          case None => false
        }

        if(newPassword.length < 8 || newPassword.toLowerCase == newPassword || !containsNumBool) {
          Future{Ok(views.html.cpass(resetPasswordForm)("Your password was too simple! Please enter a password with at least 8 characters, one upper case character, and one number.")(key))}
        }
        else {
          repo.resetPassword(username, newPassword, key).map { res =>
            val msg = if(res == -2) "The user you input does not exist!"
              else if(res == -1) "This link is not currently valid for the user you input! To get a new valid link sent, press the mobile app's \"Forgot My Password\" button again for this user."
              else if (res == 1) "Success!" else "There was an error. Check your connection and try again."
            Ok(views.html.cpass(resetPasswordForm)(msg)(key))
          } recover {
            case _ => Ok(views.html.cpass(resetPasswordForm)("There was an error. Check your connection and try again.")(key))
          }
        }
      }
    )
  }
}