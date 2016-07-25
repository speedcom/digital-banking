package com.gft.digitalbank.exchange.solution.orderBook

import com.gft.digitalbank.exchange.model.OrderDetails
import com.gft.digitalbank.exchange.model.orders.PositionOrder

object PositionOrderOps {

  implicit class PositionOrderPatch(order: PositionOrder) {

    def minusAmount(amount: Int): PositionOrder = {
      PositionOrder.builder()
        .details(new OrderDetails(
          order.getDetails.getAmount - amount,
          order.getDetails.getPrice))
        .timestamp(order.getTimestamp)
        .product(order.getProduct)
        .broker(order.getBroker)
        .client(order.getClient)
        .side(order.getSide)
        .id(order.getId)
        .build()
    }
  }
}