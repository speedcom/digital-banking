package benchmarks

import java.util.concurrent.TimeUnit

import scala.concurrent.duration._

case class DelayInMillis(delay: Long) {
  val timeUnit = TimeUnit.MILLISECONDS

  def inSeconds: Long = delay.millis.toSeconds
}

trait Timed {
  def timed[T](block: => T): DelayInMillis = {
    val startTime = System.currentTimeMillis()
    val ret       = block
    DelayInMillis(System.currentTimeMillis() - startTime)
  }
}
