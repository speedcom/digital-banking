package com.gft.digitalbank.exchange.solution

import com.gft.digitalbank.exchange.model.OrderDetails
import com.gft.digitalbank.exchange.model.orders.{ CancellationOrder, PositionOrder, Side }
import com.gft.digitalbank.exchange.solution.OrderCommand.{ CancellationOrderCommand, PositionOrderCommand }
import org.scalatest._
import scala.util.Success

class UnmarshallerTest extends FlatSpec with Matchers {

  behavior of "Unmarshaller"

  private[this] val timestamp = 123456789L
  private[this] val id = 1
  private[this] val broker = "broker"
  private[this] val client = "client"
  private[this] val product = "product"
  private[this] val side = "BUY"

  private[this] val price = 10
  private[this] val amount = 20
  private[this] val details = OrderDetails.builder().price(price).amount(amount).build()

  it should "unmarshall messages of type ORDER" in {
    val message = s"""{
      "messageType":"ORDER",
      "side":"$side",
      "id":$id,
      "timestamp":$timestamp,
      "broker":"$broker",
      "client":"$client",
      "product":"$product",
      "details": {
        "price":$price,
        "amount":$amount
      }
    }"""

    Unmarshaller(message) shouldBe Success(PositionOrderCommand(
      PositionOrder.builder()
        .id(id)
        .timestamp(timestamp)
        .broker(broker)
        .client(client)
        .product(product)
        .side(Side.BUY)
        .details(details)
      .build()
    ))

  }

  it should "unmarshall messages of type CANCEL" in {

    val cancelledOrderId = 2

    val message = s"""{
      "messageType":"CANCEL",
      "id":$id,
      "timestamp":$timestamp,
      "broker":"$broker",
      "cancelledOrderId":$cancelledOrderId
    }"""

    Unmarshaller(message) shouldBe Success(CancellationOrderCommand(
      CancellationOrder.builder()
          .timestamp(timestamp)
          .broker(broker)
          .cancelledOrderId(cancelledOrderId)
          .id(id)
          .build()
    ))
  }
}
