package com.gft.digitalbank.exchange.solution

import com.gft.digitalbank.exchange.model.OrderDetails
import com.gft.digitalbank.exchange.model.orders.PositionOrder

import java.util.Comparator

// TODO: we should probably create mutable version of it (for performance)
case class OrderBookValue(order: PositionOrder, partiallyExecuted: Boolean = false) {

  val price: Int      = order.getDetails.getPrice
  val amount: Int     = order.getDetails.getAmount
  val timestamp: Long = order.getTimestamp

  def update(minusAmount: Int): OrderBookValue = {
    val po = PositionOrder.builder()
      .details(new OrderDetails(
        order.getDetails.getAmount - minusAmount,
        order.getDetails.getPrice))
      .timestamp(order.getTimestamp)
      .product(order.getProduct)
      .broker(order.getBroker)
      .client(order.getClient)
      .side(order.getSide)
      .id(order.getId)
      .build()

    OrderBookValue(po, true)
  }
}

class BuyOrderComparator extends Comparator[OrderBookValue] {

  private val buyOrdering = Ordering.by[OrderBookValue, (Int, Long)] { case buy =>
    (buy.price * -1, buy.timestamp)
  }

  override def compare(o1: OrderBookValue, o2: OrderBookValue): Int = {
    buyOrdering.compare(o1, o2)
  }
}


class SellOrderComparator extends Comparator[OrderBookValue] {

  private val sellOrdering = Ordering.by[OrderBookValue, (Int, Long)] { case sell =>
    (sell.price, sell.timestamp)
  }

  override def compare(o1: OrderBookValue, o2: OrderBookValue): Int = {
    sellOrdering.compare(o1, o2)
  }

}