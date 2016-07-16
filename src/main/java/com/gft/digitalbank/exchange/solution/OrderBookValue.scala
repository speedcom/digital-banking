package com.gft.digitalbank.exchange.solution

import com.gft.digitalbank.exchange.model.OrderDetails
import com.gft.digitalbank.exchange.model.orders.PositionOrder

// TODO: we should probably create mutable version of it (for performance)
sealed trait OrderBookValue {
  def order: PositionOrder

  val price: Int      = order.getDetails.getPrice
  val amount: Int     = order.getDetails.getAmount
  val timestamp: Long = order.getTimestamp

  val partiallyExecuted: Boolean

  protected def update(decreaseByAmountLimit: Int): PositionOrder = {
    PositionOrder.builder()
      .details(new OrderDetails(order.getDetails.getAmount - decreaseByAmountLimit, order.getDetails.getPrice))
      .timestamp(order.getTimestamp)
      .product(order.getProduct)
      .broker(order.getBroker)
      .client(order.getClient)
      .side(order.getSide)
      .id(order.getId)
      .build()
  }
}
case class SellOrderBookValue(order: PositionOrder, partiallyExecuted: Boolean = false) extends OrderBookValue {
  def ccopy(decreaseByAmountLimit: Int) = this.copy(order = update(decreaseByAmountLimit), true)
}
case class BuyOrderBookValue(order: PositionOrder, partiallyExecuted: Boolean = false) extends OrderBookValue {
  def ccopy(decreaseByAmountLimit: Int) = this.copy(order = update(decreaseByAmountLimit), true)
}

object BuyOrderBookValue {
  val priceDescTimestampAscOrdering = Ordering.by[BuyOrderBookValue, (Int, Long)] {
    case buy => (buy.order.getDetails.getPrice, -1 * buy.order.getTimestamp)
  }
}

object SellOrderBookValue {
  val priceAscTimestampAscOrdering = Ordering.by[SellOrderBookValue, (Int, Long)] {
    case sell => (-1 * sell.order.getDetails.getPrice, -1 * sell.order.getTimestamp)
  }
}