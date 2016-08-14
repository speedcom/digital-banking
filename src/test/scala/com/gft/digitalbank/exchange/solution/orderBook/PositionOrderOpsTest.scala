package com.gft.digitalbank.exchange.solution.orderBook

import com.gft.digitalbank.exchange.model.OrderDetails
import com.gft.digitalbank.exchange.model.orders.{ModificationOrder, PositionOrder, Side}
import org.scalatest.{FlatSpec, Matchers}

class PositionOrderOpsTest extends FlatSpec with Matchers {
  import PositionOrderOps._

  behavior of "Position Order Ops"

  it should "correctly subtract amount from order and not touching the rest" in {
    // given
    val order = orderWithAmount(999)

    // when
    val modifiedOrder = order.minusAmount(AmountLimit(999))

    // then
    modifiedOrder.getId                shouldBe order.getId
    modifiedOrder.getTimestamp         shouldBe order.getTimestamp
    modifiedOrder.getBroker            shouldBe order.getBroker
    modifiedOrder.getClient            shouldBe order.getClient
    modifiedOrder.getProduct           shouldBe order.getProduct
    modifiedOrder.getSide              shouldBe order.getSide
    modifiedOrder.getDetails.getPrice  shouldBe order.getDetails.getPrice
    modifiedOrder.getDetails.getAmount shouldBe 0
  }

  it should "change order's: timestamp, broker, price and amount taken from modification order" in {
    // given
    val modificationOrder = ModificationOrder.builder()
      .id(2)
      .broker("broker-2")
      .details(OrderDetails.builder().amount(100).price(1000).build())
      .modifiedOrderId(1)
      .timestamp(1)
      .build()

    val order = orderWithAmount(10)

    // when
    val modifiedOrder = order.updateVia(modificationOrder)

    // then
    modifiedOrder.getId                shouldBe order.getId
    modifiedOrder.getTimestamp         shouldBe modificationOrder.getTimestamp
    modifiedOrder.getBroker            shouldBe modificationOrder.getBroker
    modifiedOrder.getClient            shouldBe order.getClient
    modifiedOrder.getProduct           shouldBe order.getProduct
    modifiedOrder.getSide              shouldBe order.getSide
    modifiedOrder.getDetails.getPrice  shouldBe modificationOrder.getDetails.getPrice
    modifiedOrder.getDetails.getAmount shouldBe modificationOrder.getDetails.getAmount
  }

  private def orderWithAmount(amount: Int) = {
    PositionOrder.builder()
      .id(1)
      .timestamp(1L)
      .broker("broker-2")
      .client("client-1")
      .product("GOOG")
      .side(Side.BUY)
      .details(OrderDetails.builder()
        .amount(amount)
        .price(100)
        .build())
      .build()
  }
}
