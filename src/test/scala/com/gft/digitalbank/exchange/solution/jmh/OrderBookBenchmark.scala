package com.gft.digitalbank.exchange.solution.jmh

import java.util.concurrent.atomic.AtomicInteger

import com.gft.digitalbank.exchange.model.OrderDetails
import com.gft.digitalbank.exchange.model.orders.{ PositionOrder, Side }
import com.gft.digitalbank.exchange.solution.orderBook.MutableOrderBook
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown

@State(Scope.Thread)
class OrderBookBenchmark {

  var book: MutableOrderBook = _

  var buyOrders: Array[PositionOrder]  = _
  var sellOrders: Array[PositionOrder] = _

  private def buildPositionOrder(side: Side, id: Int) = {
    PositionOrder
      .builder()
      .id(id)
      .timestamp(1)
      .broker("broker-2")
      .client("client-1")
      .product("SCALA")
      .side(Side.BUY)
      .details(OrderDetails.builder().amount(100).price(10).build())
      .build()
  }

  private def buildBuyPositionOrder(id: Int)  = buildPositionOrder(Side.BUY, id)
  private def buildSellPositionOrder(id: Int) = buildPositionOrder(Side.SELL, id)

  @Setup
  def prepare: Unit = {
    val id = new AtomicInteger(0)
    book = new MutableOrderBook("SCALA")
    buyOrders = Array.fill(100) {
      buildBuyPositionOrder(id.incrementAndGet())
    }
    sellOrders = Array.fill(100) {
      buildSellPositionOrder(id.incrementAndGet())
    }

  }

  @Benchmark
  def allBuyThenSell(): Unit = {
    buyOrders.foreach(book.handleBuyOrder)
    sellOrders.foreach(book.handleSellOrder)
    book.getTransactions
  }

  @Benchmark
  def allSellThenBuy(): Unit = {
    sellOrders.foreach(book.handleSellOrder)
    buyOrders.foreach(book.handleBuyOrder)
    book.getTransactions
  }

  @Benchmark
  def interleavedBuyFirst(): Unit = {
    sellOrders.zip(buyOrders).foreach {
      case (sell, buy) =>
        book.handleBuyOrder(buy)
        book.handleSellOrder(sell)
    }
    book.getTransactions
  }

  @Benchmark
  def interleavedSellFirst(): Unit = {
    sellOrders.zip(buyOrders).foreach {
      case (sell, buy) =>
        book.handleSellOrder(sell)
        book.handleBuyOrder(buy)
    }
    book.getTransactions
  }

}
