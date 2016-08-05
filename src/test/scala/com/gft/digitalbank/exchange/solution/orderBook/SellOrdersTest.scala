package com.gft.digitalbank.exchange.solution.orderBook

import com.gft.digitalbank.exchange.model.OrderDetails
import com.gft.digitalbank.exchange.model.orders.{PositionOrder, Side}
import org.scalatest.{FlatSpec, Matchers}

class SellOrdersTest extends FlatSpec with Matchers {

  behavior of "Sell Orders"

  it should "poll added orders in correct way" in {
    val sellOrders = new SellOrders()

    sellOrders.add(prepareSellOrder(id = 1, timestamp = 5, price = 100))
    sellOrders.add(prepareSellOrder(id = 2, timestamp = 2, price = 90))
    sellOrders.add(prepareSellOrder(id = 3, timestamp = 3, price = 90))
    sellOrders.add(prepareSellOrder(id = 4, timestamp = 6, price = 1000))
    sellOrders.add(prepareSellOrder(id = 5, timestamp = 1, price = 10))
    sellOrders.add(prepareSellOrder(id = 6, timestamp = 3, price = 10))

    sellOrders.poll().getId shouldBe 5
    sellOrders.poll().getId shouldBe 6
    sellOrders.poll().getId shouldBe 2
    sellOrders.poll().getId shouldBe 3
    sellOrders.poll().getId shouldBe 1
    sellOrders.poll().getId shouldBe 4
  }

  private def prepareSellOrder(id: Int, timestamp: Long, price: Int) = {
    PositionOrder.builder()
      .id(id)
      .timestamp(timestamp)
      .broker("broker-2")
      .client("client-1")
      .product("GOOG")
      .side(Side.SELL)
      .details(OrderDetails.builder()
        .amount(100)
        .price(price)
        .build())
      .build()
  }
}