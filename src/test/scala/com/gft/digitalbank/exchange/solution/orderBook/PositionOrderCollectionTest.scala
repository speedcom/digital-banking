package com.gft.digitalbank.exchange.solution.orderBook

import com.gft.digitalbank.exchange.model.OrderDetails
import com.gft.digitalbank.exchange.model.orders.{PositionOrder, Side}
import org.scalatest.{FlatSpec, Matchers}

class PositionOrderCollectionTest extends FlatSpec with Matchers {

  behavior of "Position Order Collection"

  it should "remove polled Order" in {
    val collection = new FakePositionOrderCollection()
    val order = randomPositionOrder
    collection.add(order)

    collection.nonEmpty shouldBe true
    collection.poll()
    collection.isEmpty  shouldBe true
  }

  it should "NOT remove peeked Order" in {
    val collection = new FakePositionOrderCollection()
    val order = randomPositionOrder
    collection.add(order)

    collection.nonEmpty shouldBe true
    collection.peekOpt  should not be empty
    collection.nonEmpty shouldBe true
  }

  it should "find order by \"modifiedOrderId\" and \"broker\"" in {
    val collection = new FakePositionOrderCollection()
    val order = randomPositionOrder
    collection.add(order)

    val found = collection.findBy(modifiedOrderId = order.getId, broker = order.getBroker)

    found shouldBe Some(order)
  }

  it should "remove order by predicate" in {
    val collection = new FakePositionOrderCollection()
    val order = randomPositionOrder
    collection.add(order)

    val removed = collection.removeIf(_.getId == order.getId)

    removed shouldBe true
  }

  private final class FakePositionOrderCollection extends PositionOrderCollection(null)

  private def randomPositionOrder = {
    PositionOrder.builder()
      .id(1)
      .timestamp(1)
      .broker("1")
      .product("GOOG")
      .side(Side.BUY)
      .client("101")
      .details(OrderDetails.builder().amount(1).price(1).build())
      .build()
  }
}
