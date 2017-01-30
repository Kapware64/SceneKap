package controllers

import play.api.mvc._
import play.api.i18n._
import play.api.data.Form
import play.api.data.Forms._

import scala.concurrent.ExecutionContext
import javax.inject._

import async._
import com.typesafe.config.ConfigFactory
import db.MongoRepo
import models.{Place, User}
import play.api.libs.json.{JsValue, Json, Writes}

/**
  * Created by NoahKaplan on 10/25/16.
  */
class PlaceController @Inject() (repo: MongoRepo, val messagesApi: MessagesApi)
                                (implicit ec: ExecutionContext) extends Controller with I18nSupport {
  val appConf = ConfigFactory.load

  val findNearby = new FindNearby(ec, repo)
  val getDetails = new Details(ec, repo)

  val BRONZE_CUTOFF: Int = appConf.getInt("scoring.bronzeCutoff")
  val SILVER_CUTOFF: Int = appConf.getInt("scoring.silverCutoff")
  val GOLD_CUTOFF: Int = appConf.getInt("scoring.goldCutoff")

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

  def index = Action {
    Ok(views.html.index(llForm)(detForm)(changeURLForm)(addPhotoForm)(postCommentForm)(voteCommentForm)(addUserForm)(loginForm)(changeScoreForm))
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

  def getAll = Action.async { implicit request =>
    repo.list().map { data =>
      Ok(Json.toJson(data))
    } recover {
      case _ => ServiceUnavailable("Database query failed")
    }
  }

  def getCutoffs = Action {
    Ok(getRankCutoffs)
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
      "Keywords" -> nonEmptyText
    )(GetDetailsForm.apply)(GetDetailsForm.unapply)
  }

  val changeURLForm: Form[ChangeURLForm] = Form {
    mapping(
      "ID" -> nonEmptyText,
      "Keywords" -> nonEmptyText,
      "URL" -> nonEmptyText
    )(ChangeURLForm.apply)(ChangeURLForm.unapply)
  }

  val addPhotoForm: Form[AddPhotoForm] = Form {
    mapping(
      "ID" -> nonEmptyText,
      "URL" -> nonEmptyText,
      "Username" -> nonEmptyText
    )(AddPhotoForm.apply)(AddPhotoForm.unapply)
  }

  val votePhotoForm: Form[VotePhotoForm] = Form {
    mapping(
      "ID" -> nonEmptyText,
      "CID" -> nonEmptyText,
      "VoteVal" -> bigDecimal,
      "Username" -> nonEmptyText
    )(VotePhotoForm.apply)(VotePhotoForm.unapply)
  }

  val postCommentForm: Form[PostCommentForm] = Form {
    mapping(
      "ID" -> nonEmptyText,
      "Text" -> nonEmptyText,
      "Username" -> nonEmptyText
    )(PostCommentForm.apply)(PostCommentForm.unapply)
  }

  val voteCommentForm: Form[VoteCommentForm] = Form {
    mapping(
      "ID" -> nonEmptyText,
      "CID" -> nonEmptyText,
      "VoteVal" -> bigDecimal,
      "Username" -> nonEmptyText
    )(VoteCommentForm.apply)(VoteCommentForm.unapply)
  }

  val addUserForm: Form[AddUserForm] = Form {
    mapping(
      "Username" -> nonEmptyText,
      "Password" -> nonEmptyText,
      "Email" -> nonEmptyText,
      "DeviceID" -> nonEmptyText
    )(AddUserForm.apply)(AddUserForm.unapply)
  }

  val loginForm: Form[LoginForm] = Form {
    mapping(
      "Username" -> nonEmptyText,
      "Password" -> nonEmptyText,
      "DeviceID" -> nonEmptyText
    )(LoginForm.apply)(LoginForm.unapply)
  }

  val changeScoreForm: Form[ChangeScoreForm] = Form {
    mapping(
      "Username" -> nonEmptyText,
      "VoteVal" -> bigDecimal
    )(ChangeScoreForm.apply)(ChangeScoreForm.unapply)
  }

  def nearbyBtn = Action { implicit request =>
    llForm.bindFromRequest.fold(
      errorForm => {
        Ok(views.html.index(errorForm)(detForm)(changeURLForm)(addPhotoForm)(postCommentForm)(voteCommentForm)(addUserForm)(loginForm)(changeScoreForm))
      },
      coord => {
        Redirect("/nearby/" + coord.lat + "/" + coord.long)
      }
    )
  }

  def detailsBtn = Action { implicit request =>
    detForm.bindFromRequest.fold(
      errorForm => {
        Ok(views.html.index(llForm)(errorForm)(changeURLForm)(addPhotoForm)(postCommentForm)(voteCommentForm)(addUserForm)(loginForm)(changeScoreForm))
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
          Ok(views.html.index(llForm)(detForm)(errorForm)(addPhotoForm)(postCommentForm)(voteCommentForm)(addUserForm)(loginForm)(changeScoreForm))
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
          Ok(views.html.index(llForm)(detForm)(changeURLForm)(errorForm)(postCommentForm)(voteCommentForm)(addUserForm)(loginForm)(changeScoreForm))
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
          Ok(views.html.index(llForm)(detForm)(changeURLForm)(addPhotoForm)(errorForm)(voteCommentForm)(addUserForm)(loginForm)(changeScoreForm))
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
          Ok(views.html.index(llForm)(detForm)(changeURLForm)(addPhotoForm)(postCommentForm)(errorForm)(addUserForm)(loginForm)(changeScoreForm))
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
          Ok(views.html.index(llForm)(detForm)(changeURLForm)(addPhotoForm)(postCommentForm)(voteCommentForm)(addUserForm)(loginForm)(changeScoreForm))
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

  def addUserBtn = Action.async { implicit request =>
    addUserForm.bindFromRequest.fold(
      errorForm => {
        repo.list().map { _ =>
          Ok(views.html.index(llForm)(detForm)(changeURLForm)(addPhotoForm)(postCommentForm)(voteCommentForm)(errorForm)(loginForm)(changeScoreForm))
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
          Ok(views.html.index(llForm)(detForm)(changeURLForm)(addPhotoForm)(postCommentForm)(voteCommentForm)(addUserForm)(errorForm)(changeScoreForm))
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
          Ok(views.html.index(llForm)(detForm)(changeURLForm)(addPhotoForm)(postCommentForm)(voteCommentForm)(addUserForm)(errorForm)(changeScoreForm))
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
          Ok(views.html.index(llForm)(detForm)(changeURLForm)(addPhotoForm)(postCommentForm)(voteCommentForm)(addUserForm)(loginForm)(errorForm))
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
}

case class CreateNearbyForm(lat: BigDecimal, long: BigDecimal)
case class GetDetailsForm(pid: String, placeKeywords: String)
case class ChangeURLForm(pid: String, placeKeywords: String, url: String)
case class AddPhotoForm(pid: String, url: String, username: String)
case class VotePhotoForm(pid: String, cid: String, voteVal: BigDecimal, username: String)
case class PostCommentForm(pid: String, text: String, username: String)
case class VoteCommentForm(pid: String, cid: String, voteVal: BigDecimal, username: String)

case class AddUserForm(username: String, password: String, email: String, deviceID: String)
case class LoginForm(username: String, password: String, deviceID: String)
case class ChangeScoreForm(username: String, voteVal: BigDecimal)