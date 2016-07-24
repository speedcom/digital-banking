package com.gft.digitalbank.exchange.solution.orderBook

import java.util.function.Predicate
import java.util.{HashSet => JHashSet}

import com.gft.digitalbank.exchange.model.orders.{CancellationOrder, ModificationOrder, PositionOrder}
import com.gft.digitalbank.exchange.model.{OrderBook, OrderDetails, Transaction}

class MutableOrderBook(product: String) {

  private val buyOrders  = new BuyOrders
  private val sellOrders = new SellOrders
  private val transactor = new OrderBookTransactor(product)

  def getTransactions: JHashSet[Transaction] = transactor.getTransactions

  def getOrderBook: OrderBook = new OrderBookPreparator(product).prepare(buyOrders, sellOrders)

  def handleBuyOrder(po: PositionOrder): Unit = {
    buyOrders.add(po)
    matchTransactions()
  }

  def handleSellOrder(po: PositionOrder): Unit = {
    sellOrders.add(po)
    matchTransactions()
  }

  def handleCancellationOrder(co: CancellationOrder): Unit = {
    buyOrders.removeIf(idMatches(co))
    sellOrders.removeIf(idMatches(co))
  }

  def handleModificationOrder(mo: ModificationOrder): Unit = {
    modifyOrder(buyOrders, mo)
    modifyOrder(sellOrders, mo)
  }

  private[this] def idMatches(co: CancellationOrder) = new Predicate[PositionOrder] {
    def test(order: PositionOrder) = order.getId == co.getCancelledOrderId && order.getBroker == co.getBroker
  }

  private[this] def idMatches(mo: ModificationOrder) = new Predicate[PositionOrder] {
    def test(order: PositionOrder) = order.getId == mo.getModifiedOrderId
  }

  private[this] def modifyOrder(order: ModificationOrder, old: PositionOrder) = {
    PositionOrder.builder()
      .details(new OrderDetails(
        order.getDetails.getAmount,
        order.getDetails.getPrice))
      .timestamp(order.getTimestamp)
      .product(old.getProduct)
      .broker(order.getBroker)
      .client(old.getClient)
      .side(old.getSide)
      .id(old.getId)
      .build()
  }

  private[this] def modifyOrder(orders: PositionOrderCollection, m: ModificationOrder): Unit = {
    for {
      obv <- orders.findBy(m.getModifiedOrderId, m.getBroker)
      modifiedOrder = modifyOrder(m, obv)
      if orders.removeIf(idMatches(m))
    } yield {
      orders.add(modifiedOrder)
      matchTransactions()
    }
  }

  private[this] def orderMinusAmount(order: PositionOrder, minusAmount: Int) = {
    PositionOrder.builder()
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
  }

  private[this] def matchTransactions(): Unit = {
    for {
      b <- buyOrders.peekOpt
      s <- sellOrders.peekOpt
      if b.getDetails.getPrice >= s.getDetails.getPrice
      amountLimit = math.min(b.getDetails.getAmount, s.getDetails.getAmount)
      priceLimit  = if(b.getTimestamp < s.getTimestamp) b.getDetails.getPrice else s.getDetails.getPrice
    } yield {
      transactor.add(b, s, amountLimit, priceLimit)
      buyOrders.poll()
      sellOrders.poll()

      (b.getDetails.getAmount > amountLimit, s.getDetails.getAmount > amountLimit) match {
        case (true, true) =>
          buyOrders  add orderMinusAmount(b, amountLimit)
          sellOrders add orderMinusAmount(s, amountLimit)
          matchTransactions()
        case (true, false) =>
          buyOrders  add orderMinusAmount(b, amountLimit)
          matchTransactions()
        case (false, true) =>
          sellOrders add orderMinusAmount(s, amountLimit)
          matchTransactions()
        case _ =>
      }
    }
  }
}