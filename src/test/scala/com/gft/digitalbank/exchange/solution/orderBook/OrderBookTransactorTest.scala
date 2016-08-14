package com.gft.digitalbank.exchange.solution.orderBook

import com.gft.digitalbank.exchange.model.OrderDetails
import com.gft.digitalbank.exchange.model.orders.{PositionOrder, Side}
import com.gft.digitalbank.exchange.solution.OrderBookProduct
import org.scalatest.{FlatSpec, Matchers}

import scala.collection.JavaConverters._

class OrderBookTransactorTest extends FlatSpec with Matchers {

  behavior of "Order Book Transactor"

  it should "increment transactions ids by one" in {
    // given
    val transactor = new OrderBookTransactor(OrderBookProduct("GOOG"))

    transactor.addTransaction(
      buy  = buildBuyPositionOrder(id = 1),
      sell = buildSellPositionOrder(id = 2),
      amountLimit = AmountLimit(100),
      priceLimit  = PriceLimit(10)
    )
    transactor.addTransaction(
      buy  = buildBuyPositionOrder(id = 3),
      sell = buildSellPositionOrder(id = 4),
      amountLimit = AmountLimit(8),
      priceLimit  = PriceLimit(15)
    )

    // when
    val transactions = transactor.getTransactions

    // then
    transactions.transactions.asScala.map(_.getId) shouldBe Set(1, 2)
  }

  private def buildPositionOrder(side: Side, id: Int) = {
    PositionOrder.builder()
      .id(id)
      .timestamp(1)
      .broker("broker-2")
      .client("client-1")
      .product("GOOG")
      .side(Side.BUY)
      .details(OrderDetails.builder()
        .amount(100)
        .price(10)
        .build())
      .build()
  }

  private def buildBuyPositionOrder(side: Side = Side.BUY, id: Int) = buildPositionOrder(side, id)
  private def buildSellPositionOrder(side: Side = Side.SELL, id: Int) = buildPositionOrder(side, id)
}
