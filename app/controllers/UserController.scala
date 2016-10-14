package controllers

import play.api.mvc._
import play.api.i18n._
import play.api.data.Form
import play.api.data.validation.Constraints._
import models._
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}
import play.api.data.Forms._
import dal._

import scala.concurrent.{ExecutionContext}
import javax.inject._

import play.api.libs.json._
import play.api.libs.functional.syntax._

class UserController @Inject() (repo: UserRepository, val messagesApi: MessagesApi)
                                 (implicit ec: ExecutionContext) extends Controller with I18nSupport{
  case class UserEntry(id: Long, username: String, settings: String, lastlogged: String, password: String)

  implicit val userReads: Reads[UserEntry] = (
    (JsPath \ "id").read[Long] and
      (JsPath \ "username").read[String] and
      (JsPath \ "settings").read[String] and
      (JsPath \ "lastlogged").read[String] and
      (JsPath \ "password").read[String]
    )(UserEntry.apply _)

  val genderCheckConstraint: Constraint[String] = Constraint("constraints.genderscheck")({
    str =>
      // you have access to all the fields in the form here and can
      // write complex logic here
      if (str == "M" || str == "F") {
        Valid
      } else {
        Invalid(Seq(ValidationError("You must enter M or F")))
      }
  })

  val validJSON: Constraint[String] = Constraint("constraints.json") { inputjson =>
    try {
      val _ = Json.parse(inputjson).as[List[UserEntry]]
      Valid
    } catch {
      case e: Exception => Invalid(Seq(ValidationError("Input is not in correct JSON format")))
    }
  }

  /**
   * The mapping for the person form.
   */
  val userForm: Form[CreateUserForm] = Form {
    mapping(
      "Username" -> nonEmptyText,
      "Settings" -> nonEmptyText,
      "Last Logged" -> nonEmptyText,
      "Password" -> nonEmptyText
    )(CreateUserForm.apply)(CreateUserForm.unapply)
  }

  val jsonForm: Form[CreateJsonForm] = Form {
    mapping (
      "JSON" -> text.verifying(validJSON)
    )(CreateJsonForm.apply)(CreateJsonForm.unapply)
  }

  /**
   * The index action.
   */
  def index = Action.async {
    repo.list().map { people =>
      Ok(views.html.index(userForm)(people.length)(jsonForm))
    }
  }

  def descPersons = Action.async { implicit request =>
    repo.list().map { _ =>
      Redirect(routes.UserController.getPersons())
    }
  }

  def resetAll = Action.async { implicit request =>
    repo.deleteAll().map { _ =>
      Redirect(routes.UserController.index)
    }
  }

  def constPeople(rawppl: List[UserEntry]): List[User] = {
    var ret = List[User]()
    for (rawperson <- rawppl) {
      ret =  User(1, rawperson.username, rawperson.settings, rawperson.lastlogged, rawperson.password) :: ret
    }
    ret
  }

  def inputJSON = Action.async { implicit request =>
    jsonForm.bindFromRequest.fold(
      errorForm => {
        repo.list().map { people =>
          Ok(views.html.index(userForm)(people.length)(errorForm))
        }
      },
      JSON => {
        repo.createmult(constPeople(Json.parse(JSON.json).as[List[UserEntry]])).map { _ =>
          Redirect(routes.UserController.index)
        }
      }
    )
  }

  def graphIt = Action.async { implicit request =>
    repo.list().map { people =>
      Ok(views.html.barchart(List.range(0, 150)))
    }
  }

  /**
   * The add person action.
   *
   * This is asynchronous, since we're invoking the asynchronous methods on PersonRepository.
   */
  def addPerson = Action.async { implicit request =>
    // Bind the form first, then fold the result, passing a function to handle errors, and a function to handle succes.
    userForm.bindFromRequest.fold(
      // The error function. We return the index page with the error form, which will render the errors.
      // We also wrap the result in a successful future, since this action is synchronous, but we're required to return
      // a future because the person creation function returns a future.
      errorForm => {
        repo.list().map { people =>
          Ok(views.html.index(errorForm)(people.length)(jsonForm))
        }
      },
      // There were no errors in the from, so create the person.
      user => {
        repo.create(user.username, user.settings, user.lastlogged, user.password).map { _ =>
          // If successful, we simply redirect to the index page.
          Redirect(routes.UserController.index)
        }
      }
    )
  }

  /**
   * A REST endpoint that gets all the people as JSON.
   */
  def getPersons = Action.async {
  	repo.list().map { people =>
      Ok(Json.toJson(people))
    }
  }
}

/**
 * The create person form.
 *
 * Generally for forms, you should define separate objects to your models, since forms very often need to present data
 * in a different way to your models.  In this case, it doesn't make sense to have an id parameter in the form, since
 * that is generated once it's created.
 */
case class CreateUserForm(username: String, settings: String, lastlogged: String, password: String)

case class CreateJsonForm(json: String)
