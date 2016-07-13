package com.gft.digitalbank.exchange.solution

import javax.jms.TextMessage

import com.gft.digitalbank.exchange.model.OrderDetails
import com.gft.digitalbank.exchange.model.orders._
import com.gft.digitalbank.exchange.solution.OrderCommand.{CancellationOrderCommand, ModificationOrderCommand, PositionOrderCommand, ShutdownOrderCommand}

import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.util.Try

object Unmarshaller {

  def apply(txt: TextMessage): Try[OrderCommand] = Try {
    val json = txt.getText.parseJson.asJsObject

    json.getFields("messageType").headOption match {
      case Some(JsString("ORDER"))                 => json.toPositionOrder
      case Some(JsString("MODIFICATION"))          => json.toModificationOrder
      case Some(JsString("CANCEL"))                => json.toCancellationOrder
      case Some(JsString("SHUTDOWN_NOTIFICATION")) => json.toShutdownNotification
      case other                                   => throw new IllegalArgumentException(s"Message ${txt.getText} doesn't contain field messageType")
    }
  }

  private implicit class JsonOps(json: JsObject) {

    final def toPositionOrder: PositionOrderCommand = json.getFields("messageType", "side", "id", "timestamp", "broker", "client", "product", "details") match {
      case Seq(JsString(_), JsString(side), JsNumber(id), JsNumber(timestamp), JsString(broker), JsString(client), JsString(product), JsObject(details)) =>
        (details("amount"), details("price")) match {
          case (JsNumber(amount), JsNumber(price)) =>
            PositionOrderCommand(PositionOrder.builder()
              .timestamp(timestamp.toLong)
              .id(id.toInt)
              .broker(broker)
              .client(client)
              .product(product)
              .side(if(side == "BUY") Side.BUY else Side.SELL)
              .details(new OrderDetails(amount.toInt, price.toInt))
              .build())
        }
    }

    final def toCancellationOrder: CancellationOrderCommand = json.getFields("messageType", "id", "timestamp", "broker", "cancelledOrderId") match {
      case Seq(JsString(_), JsNumber(id), JsNumber(timestamp), JsString(broker), JsNumber(cancelledOrderId)) =>
        CancellationOrderCommand(CancellationOrder.builder()
          .messageType(MessageType.CANCEL)
          .timestamp(timestamp.toLong)
          .broker(broker)
          .cancelledOrderId(cancelledOrderId.toInt)
          .id(id.toInt)
          .build())
    }

    final def toShutdownNotification: ShutdownOrderCommand = json.getFields("messageType", "timestamp", "id", "broker") match {
      case Seq(JsString(_), JsNumber(timestamp), JsNumber(id), JsString(broker)) =>
        ShutdownOrderCommand(ShutdownNotification.builder()
          .timestamp(timestamp.toLong)
          .broker(broker)
          .id(id.toInt)
          .build())
    }

    final def toModificationOrder: ModificationOrderCommand = json.getFields("messageType", "id", "timestamp", "broker", "modifiedOrderId", "details") match {
      case Seq(JsString(_), JsNumber(id), JsNumber(timestamp), JsString(broker), JsNumber(modifiedOrderId), JsObject(details)) =>
        (details("amount"), details("price")) match {
          case (JsNumber(amount), JsNumber(price)) =>
            ModificationOrderCommand(ModificationOrder.builder()
              .timestamp(timestamp.toLong)
              .id(id.toInt)
              .broker(broker)
              .modifiedOrderId(modifiedOrderId.toInt)
              .details(new OrderDetails(amount.toInt, price.toInt))
              .build())
        }
    }
  }

}
