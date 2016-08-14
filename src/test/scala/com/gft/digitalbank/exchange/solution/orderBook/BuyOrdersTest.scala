package com.gft.digitalbank.exchange.solution.orderBook

import com.gft.digitalbank.exchange.model.OrderDetails
import com.gft.digitalbank.exchange.model.orders.{PositionOrder, Side}
import org.scalatest.{FlatSpec, Matchers}

class BuyOrdersTest extends FlatSpec with Matchers {

  behavior of "Buy Orders"

  it should "poll added orders in correct way" in {
    val buyOrders = new BuyOrders()

    buyOrders.add(prepareBuyOrder(id = 1, timestamp = 5, price = 100))
    buyOrders.add(prepareBuyOrder(id = 2, timestamp = 2, price = 90))
    buyOrders.add(prepareBuyOrder(id = 3, timestamp = 3, price = 90))
    buyOrders.add(prepareBuyOrder(id = 4, timestamp = 6, price = 1000))
    buyOrders.add(prepareBuyOrder(id = 5, timestamp = 1, price = 10))
    buyOrders.add(prepareBuyOrder(id = 6, timestamp = 3, price = 10))

    buyOrders.poll().getId shouldBe 4
    buyOrders.poll().getId shouldBe 1
    buyOrders.poll().getId shouldBe 2
    buyOrders.poll().getId shouldBe 3
    buyOrders.poll().getId shouldBe 5
    buyOrders.poll().getId shouldBe 6
  }

  private def prepareBuyOrder(id: Int, timestamp: Long, price: Int) = {
    PositionOrder.builder()
      .id(id)
      .timestamp(timestamp)
      .broker("broker-2")
      .client("client-1")
      .product("GOOG")
      .side(Side.BUY)
      .details(OrderDetails.builder()
        .amount(100)
        .price(price)
        .build())
      .build()
  }
}
