package benchmarks

import com.gft.digitalbank.exchange.model.orders.Side
import com.gft.digitalbank.exchange.solution.OrderBookProduct
import com.gft.digitalbank.exchange.solution.orderBook.MutableOrderBook

object ThroughputBenchmarkApp extends Timed with PositionOrderHelper {

  def main(args: Array[String]): Unit = {
    val commandCount = args(0).toInt

    jvmWarmUp(commandCount = 100000)

    val delay = timed(runTestingScenario(commandCount))

    println(
      s"""
         |Processed ${2*commandCount} commands ($commandCount BUY, $commandCount SELL)
         |in ${delay.inSeconds}s (${delay.delay}ms)
         |Throughput: ${2*commandCount / delay.inSeconds } operation/sec
       """.stripMargin
    )
  }

  def runTestingScenario(size: Int): Unit = {
    val orderBook    = new MutableOrderBook(OrderBookProduct("GOOG"))

    for (i <- 1 to size) {
      orderBook.handleBuyOrder(buildPositionOrder(Side.BUY, i)())
      orderBook.handleSellOrder(buildPositionOrder(Side.SELL, i)())
    }
  }

  def jvmWarmUp(commandCount: Int) = runTestingScenario(size = commandCount)
}
