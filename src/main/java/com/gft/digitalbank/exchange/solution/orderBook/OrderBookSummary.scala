package com.gft.digitalbank.exchange.solution.orderBook

import java.util.{ArrayList => JArrayList}

import com.gft.digitalbank.exchange.model.{OrderBook, OrderEntry}
import com.gft.digitalbank.exchange.model.orders.PositionOrder

private[orderBook] class OrderBookSummary(product: String) {

  def prepare(buyOrders: BuyOrders, sellOrders: SellOrders): OrderBook = {
    OrderBook.builder()
      .product(product)
      .buyEntries(prepareEntries(buyOrders))
      .sellEntries(prepareEntries(sellOrders))
      .build()
  }

  private[this] def prepareEntries(orders: PositionOrderCollection) = {
    val entries = new JArrayList[OrderEntry]
    var id = 1

    while(orders.nonEmpty) {
      entries.add(toOrderEntry(orders.poll(), id))
      id += 1
    }
    entries
  }

  private[this] def toOrderEntry(order: PositionOrder, id: Int) = {
    OrderEntry.builder()
      .id(id)
      .amount(order.getDetails.getAmount)
      .price(order.getDetails.getPrice)
      .client(order.getClient)
      .broker(order.getBroker)
      .build()
  }
}