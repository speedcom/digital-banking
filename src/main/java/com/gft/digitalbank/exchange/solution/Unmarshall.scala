package com.gft.digitalbank.exchange.solution

import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.concurrent.ExecutionContext
object Unmarshall {

  val messageTypeField = "messageType"

  private[this] implicit val detailsReader: JsonFormat[Details] = jsonFormat2(Details.apply)
  private[this] implicit val buyReader: JsonReader[Buy]         = jsonFormat6(Buy.apply)
  private[this] implicit val sellReader: JsonReader[Sell]       = jsonFormat6(Sell.apply)
  private[this] implicit val modifyReader: JsonReader[Modify]   = jsonFormat5(Modify.apply)
  private[this] implicit val cancelReader: JsonReader[Cancel]   = jsonFormat4(Cancel.apply)

  def jsonToJsonCommand(jsObject: JsObject): Either[ShutdownNotification.type, OrderCommand] = {
    val commandType = jsObject.fields.getOrElse(messageTypeField, throw new IllegalArgumentException(s"Message doesn't contain field '$messageTypeField"))
    commandType match {
      case JsString("SHUTDOWN_NOTIFICATION") => Left(ShutdownNotification)
      case _                                 => Right(jsonToOrderCommand(jsObject))
    }
  }

  @inline
  final private[this] def jsonToOrderCommand(jsObject: JsObject): OrderCommand = {
    val commandType = jsObject.fields(messageTypeField)

    commandType match {
      case JsString("ORDER") =>
        val side = jsObject.fields("side").convertTo[String]
        if (side == "BUY") {
          jsObject.convertTo[Buy]
        } else {
          jsObject.convertTo[Sell]
        }
      case _ => println(jsObject); ???
    }
  }
}
