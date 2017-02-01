import play.api.data.Form
import play.api.data.Forms._

package object controllers {
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

  val changePasswordForm: Form[ChangePasswordForm] = Form {
    mapping(
      "Username" -> nonEmptyText,
      "OldPassword" -> nonEmptyText,
      "NewPassword" -> nonEmptyText
    )(ChangePasswordForm.apply)(ChangePasswordForm.unapply)
  }

  val forgotPasswordForm: Form[ForgotPasswordForm] = Form {
    mapping(
      "Username" -> nonEmptyText,
      "Email" -> nonEmptyText
    )(ForgotPasswordForm.apply)(ForgotPasswordForm.unapply)
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
  case class ChangePasswordForm(username: String, oldPassword: String, newPassword: String)
  case class ForgotPasswordForm(username: String, email: String)
}
