package com.gft.digitalbank.exchange.solution.jmh

import java.util.concurrent.atomic.AtomicInteger

import com.gft.digitalbank.exchange.model.OrderDetails
import com.gft.digitalbank.exchange.model.orders.{PositionOrder, Side}
import com.gft.digitalbank.exchange.solution.OrderBookProduct
import com.gft.digitalbank.exchange.solution.orderBook.{AmountLimit, OrderBookTransactor, PriceLimit}
import org.openjdk.jmh.annotations._

@State(Scope.Thread)
class OrderBookTransactorBenchmark {

  var transactionParams: Array[(PositionOrder, PositionOrder)] = _

  var orderBookTransactor: OrderBookTransactor = _

  var amountLimit = AmountLimit(1)
  var priceLimit  = PriceLimit(1)

  @inline
  final private[this] def buildPositionOrder(side: Side, id: Int, amount:Int = 100, price:Int = 10) = {
    PositionOrder
      .builder()
      .id(id)
      .timestamp(1)
      .broker("broker")
      .client("client-"+ id)
      .product("SCALA")
      .side(Side.BUY)
      .details(OrderDetails.builder().amount(amount).price(price).build())
      .build()
  }

  @inline
  final private[this] def buildBuyPositionOrder(id: Int)  = buildPositionOrder(Side.BUY, id)

  @inline
  final private[this] def buildSellPositionOrder(id: Int) = buildPositionOrder(Side.SELL, id)

  @Setup
  def prepare: Unit = {
    val id = new AtomicInteger(0)
    val buyOrders = Array.fill(100)(buildBuyPositionOrder(id.incrementAndGet()))
    val sellOrders = Array.fill(100)(buildSellPositionOrder(id.incrementAndGet()))

    transactionParams = buyOrders zip sellOrders
  }

  @Benchmark
  def a: Unit = {
    orderBookTransactor = new OrderBookTransactor(OrderBookProduct("SCALA"))
    transactionParams.foreach { case (buy, sell) =>
      orderBookTransactor.addTransaction(buy, sell, amountLimit, priceLimit)
    }
    orderBookTransactor.getTransactions
  }
}
