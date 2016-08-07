package com.gft.digitalbank.exchange.solution.orderBook

import com.gft.digitalbank.exchange.model.OrderDetails
import com.gft.digitalbank.exchange.model.orders.{PositionOrder, Side}
import org.scalatest.{FlatSpec, Matchers}

class PositionOrderCollectionTest extends FlatSpec with Matchers {

  behavior of "Position Order Collection"

  it should "remove Order when its polled" in {
    val collection = new FakePositionOrderCollection()

    collection.add(
      PositionOrder.builder()
        .id(1)
        .timestamp(1)
        .broker("1")
        .product("GOOG")
        .side(Side.BUY)
        .client("101")
        .details(OrderDetails.builder().amount(1).price(1).build())
        .build()
    )

    collection.nonEmpty shouldBe true

    collection.poll()

    collection.isEmpty shouldBe true
  }

  private final class FakePositionOrderCollection extends PositionOrderCollection(null)

}