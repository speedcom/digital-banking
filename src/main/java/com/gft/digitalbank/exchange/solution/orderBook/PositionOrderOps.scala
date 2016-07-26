package com.gft.digitalbank.exchange.solution.orderBook

import com.gft.digitalbank.exchange.model.OrderDetails
import com.gft.digitalbank.exchange.model.orders.{ModificationOrder, PositionOrder}

object PositionOrderOps {

  implicit class PositionOrderPatch(po: PositionOrder) {

    def minusAmount(limit: AmountLimit): PositionOrder = {
      PositionOrder.builder()
        .details(new OrderDetails(
          po.getDetails.getAmount - limit.amount,
          po.getDetails.getPrice))
        .timestamp(po.getTimestamp)
        .product(po.getProduct)
        .broker(po.getBroker)
        .client(po.getClient)
        .side(po.getSide)
        .id(po.getId)
        .build()
    }

    def updateVia(mo: ModificationOrder): PositionOrder = {
      PositionOrder.builder()
        .details(new OrderDetails(
          mo.getDetails.getAmount,
          mo.getDetails.getPrice))
        .timestamp(mo.getTimestamp)
        .product(po.getProduct)
        .broker(mo.getBroker)
        .client(po.getClient)
        .side(po.getSide)
        .id(po.getId)
        .build()
    }
  }
}