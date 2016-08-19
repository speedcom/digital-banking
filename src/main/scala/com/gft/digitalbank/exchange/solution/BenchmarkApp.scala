package com.gft.digitalbank.exchange.solution

import java.util.concurrent.atomic.AtomicInteger

import com.gft.digitalbank.exchange.model.orders.{PositionOrder, Side}
import com.gft.digitalbank.exchange.model.OrderDetails
import com.gft.digitalbank.exchange.solution.orderBook.MutableOrderBook

import scala.concurrent.duration._

object BenchmarkApp extends App {

  def timed[T](block: => T): T = {
    val startTime = System.currentTimeMillis()
    val ret       = block
    val took      = (System.currentTimeMillis() - startTime).millis
    println(s"*** Processing took ${took.toSeconds}s (${took.toMillis}ms)")
    ret
  }

  def runTestingScenario(mutableOrderBook: MutableOrderBook, size: Int): Unit = {
    val orderCounter = new AtomicInteger(0)
    def id           = orderCounter.incrementAndGet()
    def send(positionOrder: PositionOrder) =
      if (positionOrder.getSide == Side.BUY)
        mutableOrderBook.handleBuyOrder(positionOrder)
      else
        mutableOrderBook.handleSellOrder(positionOrder)

    for (i <- 1 to size) {
      send(buildPositionOrder(Side.BUY, id)())
      send(buildPositionOrder(Side.SELL, id)())
    }

    for (i <- 1 to size) {
      send(buildPositionOrder(Side.SELL, id)())
      send(buildPositionOrder(Side.BUY, id)())
    }

    for (i <- 1 to size) {
      send(buildPositionOrder(Side.SELL, id)())
    }
    for (i <- 1 to size) {
      send(buildPositionOrder(Side.BUY, id)())
    }

    for (i <- 1 to size) {
      send(buildPositionOrder(Side.BUY, id)())
    }
    for (i <- 1 to size) {
      send(buildPositionOrder(Side.SELL, id)())
    }

    send(buildPositionOrder(Side.SELL, id, 100 * size)())
    for (i <- 1 to size) {
      send(buildPositionOrder(Side.BUY, id)())
    }

    send(buildPositionOrder(Side.BUY, id, 100 * size)())
    for (i <- 1 to size) {
      send(buildPositionOrder(Side.SELL, id)())
    }
  }

  @inline
  private[this] def buildPositionOrder(side: Side, id: Int, amount: Int = 100, price: Int = 10) = {
    val timeCounter = new AtomicInteger(1)

    () =>
      PositionOrder
        .builder()
        .id(id)
        .timestamp(timeCounter.incrementAndGet())
        .broker("broker")
        .client("client-" + id)
        .product("SCL")
        .side(side)
        .details(OrderDetails.builder().amount(amount).price(price).build())
        .build()
  }

  def runBenchmark(size: Int = 10): Unit = {
    println(s"Running benchmark for size=$size")

    val orderBook = new MutableOrderBook(OrderBookProduct("SCL"))
    runTestingScenario(orderBook, size)
  }

  for (size <- List(1000, 10 * 1000, 100 * 1000, 1000 * 1000)) {
    timed(runBenchmark(size))
  }
}
