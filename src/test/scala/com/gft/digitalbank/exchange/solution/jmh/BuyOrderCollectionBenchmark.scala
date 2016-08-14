package com.gft.digitalbank.exchange.solution.jmh

import java.util.concurrent.atomic.AtomicInteger

import com.gft.digitalbank.exchange.model.OrderDetails
import com.gft.digitalbank.exchange.model.orders.{PositionOrder, Side}
import com.gft.digitalbank.exchange.solution.orderBook.BuyOrders
import org.openjdk.jmh.annotations._

@State(Scope.Thread)
class BuyOrderCollectionBenchmark {

  var buyOrders: BuyOrders = _
  var orders: Array[PositionOrder] = _
  @inline
  final private[this] def buildBuyOrder(id: Int, amount:Int = 100, price:Int = 10) = {
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

  @Setup
  def prepare: Unit = {
    val id = new AtomicInteger(0)
    buyOrders = new BuyOrders
    orders = Array.fill(100)(buildBuyOrder(id.incrementAndGet()))
    orders.foreach(buyOrders.add)
  }

  @Benchmark
  def add: Unit = {
    orders.foreach(buyOrders.add)
  }

  @Benchmark
  def pollOneByOne: Unit = {
    while(buyOrders.nonEmpty)
      buyOrders.poll()
  }

  @Benchmark
  def peekFirst: Unit = {
    buyOrders.peekOpt
  }

  @Benchmark
  def peekThenPollOneByOne: Unit = {
    while(buyOrders.nonEmpty) {
      buyOrders.peekOpt
      buyOrders.poll()
    }
  }
}
