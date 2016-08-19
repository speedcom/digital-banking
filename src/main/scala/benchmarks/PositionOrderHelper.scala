package benchmarks

import java.util.concurrent.atomic.AtomicInteger

import com.gft.digitalbank.exchange.model.OrderDetails
import com.gft.digitalbank.exchange.model.orders.{PositionOrder, Side}

/**
  * Created by new on 19.08.2016.
  */
trait PositionOrderHelper {

  @inline
  def buildPositionOrder(side: Side, id: Int, amount: Int = 100, price: Int = 10) = {
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

}
