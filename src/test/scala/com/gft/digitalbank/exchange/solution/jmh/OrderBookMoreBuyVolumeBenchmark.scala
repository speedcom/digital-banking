package com.gft.digitalbank.exchange.solution.jmh

import java.util.concurrent.atomic.AtomicInteger

import com.gft.digitalbank.exchange.model.OrderDetails
import com.gft.digitalbank.exchange.model.orders.{PositionOrder, Side}
import com.gft.digitalbank.exchange.solution.OrderBookProduct
import com.gft.digitalbank.exchange.solution.orderBook.MutableOrderBook
import org.openjdk.jmh.annotations._

@State(Scope.Thread)
class OrderBookMoreBuyVolumeBenchmark {

  var book: MutableOrderBook = _

  var buyOrders: Array[PositionOrder]  = _
  var sellOrders: Array[PositionOrder] = _

  @inline
  final private[this] def buildPositionOrder(side: Side, id: Int, amount:Int = 100, price:Int = 10) = {
    PositionOrder
      .builder()
      .id(id)
      .timestamp(1)
      .broker("broker")
      .client("client-"+ id)
      .product("SCALA")
      .side(side)
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
    book = new MutableOrderBook(OrderBookProduct("SCALA"))
    buyOrders = Array.fill(100) {
      buildBuyPositionOrder(id.incrementAndGet())
    }
    sellOrders = buyOrders.map{ sell =>
      buildPositionOrder(Side.SELL, id.incrementAndGet(), sell.getDetails.getAmount-1, sell.getDetails.getPrice)
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
