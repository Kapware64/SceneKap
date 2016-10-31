import play.api.libs.json._
import scala.io

/**
  * Created by NoahKaplan on 10/26/16.
  */
package object async {
  val ABOUT_LINK_KEYWORDS: List[String] = List[String]("About", "about", "ABOUT")
  val ABOUT_KEYWORDS: List[String] = List[String]("We, Our")
  val CONNECT_TIMEOUT = 5000
  val READ_TIMEOUT = 5000
  val MAX_PHOTO_WIDTH = 400
  val GP_KEY = "AIzaSyBuZtwpHo3XQpxPOFjALeUgazV_QxZudUU"
  val GP_KEY_DET = "AIzaSyAls_qyBvY6SG919zH7S3Iy9RMBbfypRgw"

  def getStrProp(str: String, json: JsValue, default: String): String = {
    json \ str match {
      case JsDefined(JsString(prop)) => prop
      case _ => default
    }
  }

  def getNumProp(str: String, json: JsValue, default: BigDecimal): BigDecimal = {
    json \ str match {
      case JsDefined(JsNumber(prop)) => prop
      case _ => default
    }
  }

  @throws(classOf[java.io.IOException])
  @throws(classOf[java.net.SocketTimeoutException])
  def get(url: String, connectTimeout: Int = CONNECT_TIMEOUT, readTimeout: Int = READ_TIMEOUT, requestMethod: String = "GET") = {
    import java.net.{URL, HttpURLConnection}
    val connection = new URL(url).openConnection.asInstanceOf[HttpURLConnection]
    connection.setConnectTimeout(connectTimeout)
    connection.setReadTimeout(readTimeout)
    connection.setRequestMethod(requestMethod)
    val inputStream = connection.getInputStream
    val content = io.Source.fromInputStream(inputStream).mkString
    if (inputStream != null) inputStream.close()
    content
  }
}
