package benchmarks

import java.util.concurrent.atomic.AtomicInteger

import com.gft.digitalbank.exchange.model.orders.{PositionOrder, Side}
import com.gft.digitalbank.exchange.solution.OrderBookProduct
import com.gft.digitalbank.exchange.solution.orderBook.MutableOrderBook

object BatchBenchmarkApp extends App with Timed with PositionOrderHelper {

  def runTestingScenario(mutableOrderBook: MutableOrderBook, size: Int): Unit = {
    val orderCounter = new AtomicInteger(0)
    def id           = orderCounter.incrementAndGet()
    def send(positionOrder: PositionOrder) =
      if (positionOrder.getSide == Side.BUY)
        mutableOrderBook.handleBuyOrder(positionOrder)
      else
        mutableOrderBook.handleSellOrder(positionOrder)

    for (i <- 1 to size) {
      send(buildPositionOrder(Side.BUY, i)())
      send(buildPositionOrder(Side.SELL, i)())
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

  def runBenchmark(size: Int = 10): Unit = {
    println(s"Running benchmark for size=$size")

    val orderBook = new MutableOrderBook(OrderBookProduct("SCL"))
    runTestingScenario(orderBook, size)
  }

  for (size <- List(1000, 10 * 1000, 100 * 1000, 1000 * 1000)) {
    val delay = timed(runBenchmark(size))
    println(s"*** Processing took ${delay.inSeconds}s (${delay.delay}ms)")
  }
}
