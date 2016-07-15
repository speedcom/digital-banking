package com.gft.digitalbank.exchange.solution

import com.gft.digitalbank.exchange.model.OrderDetails
import com.gft.digitalbank.exchange.model.orders.PositionOrder

// TODO: we should probably create mutable version of it (for performance)
sealed trait OrderValue {
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
case class SellOrderValue(order: PositionOrder, partiallyExecuted: Boolean = false) extends OrderValue {
  def ccopy(decreaseByAmountLimit: Int) = this.copy(order = update(decreaseByAmountLimit), true)
}
case class BuyOrderValue(order: PositionOrder, partiallyExecuted: Boolean = false) extends OrderValue {
  def ccopy(decreaseByAmountLimit: Int) = this.copy(order = update(decreaseByAmountLimit), true)
}

object BuyOrderValue {
  val priceDescTimestampAscOrdering = Ordering.by[BuyOrderValue, (Int, Long)] {
    case buy => (buy.order.getDetails.getPrice, -1 * buy.order.getTimestamp)
  }
}

object SellOrderValue {
  val priceAscTimestampAscOrdering = Ordering.by[SellOrderValue, (Int, Long)] {
    case sell => (-1 * sell.order.getDetails.getPrice, -1 * sell.order.getTimestamp)
  }
}