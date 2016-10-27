import play.api.libs.json._

/**
  * Created by NoahKaplan on 10/26/16.
  */
package object async {
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
}
