package com.gft.digitalbank.exchange.solution

import javax.jms.TextMessage

import com.gft.digitalbank.exchange.model.OrderDetails
import com.gft.digitalbank.exchange.model.orders._
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.util.{Failure, Try}

object Unmarshaller {

  def apply(txt: TextMessage): Try[BrokerMessage] = {
    val json = txt.getText.parseJson.asJsObject

    json.getFields("messageType").headOption match {
      case Some(JsString("ORDER"))                 => Try(json2PositionOrder(json))
      case Some(JsString("MODIFICATION"))          => Try(json2ModificationOrder(json))
      case Some(JsString("CANCEL"))                => Try(json2CancellationOrder(json))
      case Some(JsString("SHUTDOWN_NOTIFICATION")) => Try(json2ShutdownNotification(json))
      case other                                   => Failure(new IllegalArgumentException(s"Message ${txt.getText} doesn't contain field messageType"))
    }
  }

  @inline
  final private def json2PositionOrder(json: JsObject): PositionOrder = json.getFields("messageType", "side", "id", "timestamp", "broker", "client", "product", "details") match {
    case Seq(JsString(_), JsString(side), JsNumber(id), JsNumber(timestamp), JsString(broker), JsString(client), JsString(product), Seq(JsNumber(amount), JsNumber(price))) =>
      new PositionOrder.PositionOrderBuilder()
        .timestamp(timestamp.toLong)
        .id(id.toInt)
        .broker(broker)
        .client(client)
        .product(product)
        .side(if(side == "BUY") Side.BUY else Side.SELL)
        .details(new OrderDetails(amount.toInt, price.toInt))
        .build()
  }

  @inline
  final private def json2CancellationOrder(json: JsObject): CancellationOrder = json.getFields("messageType", "id", "timestamp", "broker", "cancelledOrderId") match {
    case Seq(JsString(_), JsNumber(id), JsNumber(timestamp), JsString(broker), JsNumber(cancelledOrderId)) =>
      new CancellationOrder.CancellationOrderBuilder()
        .messageType(MessageType.CANCEL)
        .timestamp(timestamp.toLong)
        .broker(broker)
        .cancelledOrderId(cancelledOrderId.toInt)
        .id(id.toInt)
        .build()
  }

  @inline
  final private def json2ShutdownNotification(json: JsObject): ShutdownNotification = json.getFields("messageType", "timestamp", "id", "broker") match {
    case Seq(JsString(_), JsNumber(timestamp), JsNumber(id), JsString(broker)) =>
      new ShutdownNotification.ShutdownNotificationBuilder()
        .timestamp(timestamp.toLong)
        .broker(broker)
        .id(id.toInt)
        .build()
  }

  @inline
  final private def json2ModificationOrder(json: JsObject): ModificationOrder = json.getFields("messageType", "id", "timestamp", "broker", "modifiedOrderId", "details") match {
    case Seq(JsString(_), JsNumber(id), JsNumber(timestamp), JsString(broker), JsNumber(modifiedOrderId), Seq(JsNumber(amount), JsNumber(price))) =>
      new ModificationOrder.ModificationOrderBuilder()
        .timestamp(timestamp.toLong)
        .id(id.toInt)
        .broker(broker)
        .modifiedOrderId(modifiedOrderId.toInt)
        .details(new OrderDetails(amount.toInt, price.toInt))
        .build()
  }
}
