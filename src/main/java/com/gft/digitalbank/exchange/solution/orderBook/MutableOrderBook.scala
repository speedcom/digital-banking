package com.gft.digitalbank.exchange.solution.orderBook

import java.util.function.Predicate
import java.util.{HashSet => JHashSet}

import com.gft.digitalbank.exchange.model.orders.{CancellationOrder, ModificationOrder, PositionOrder}
import com.gft.digitalbank.exchange.model.{OrderBook, OrderDetails, Transaction}
import PositionOrderOps._

class MutableOrderBook(product: String) {

  private val buyOrders  = new BuyOrders
  private val sellOrders = new SellOrders
  private val transactor = new OrderBookTransactor(product)

  def getTransactions: Transactions = transactor.getTransactions

  def getOrderBook: OrderBook = new OrderBookPreparator(product).prepare(buyOrders, sellOrders)

  def handleBuyOrder(po: PositionOrder): Unit = {
    buyOrders.add(po)
    matchOrders()
  }

  def handleSellOrder(po: PositionOrder): Unit = {
    sellOrders.add(po)
    matchOrders()
  }

  def handleCancellationOrder(co: CancellationOrder): Unit = {
    val idAndBrokerSame = (po: PositionOrder) => po.getId == co.getCancelledOrderId && po.getBroker == co.getBroker
    buyOrders.removeIf(idAndBrokerSame)
    sellOrders.removeIf(idAndBrokerSame)
  }

  def handleModificationOrder(mo: ModificationOrder): Unit = {
    modifyOrder(buyOrders, mo)
    modifyOrder(sellOrders, mo)
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

  private[this] def modifyOrder(orders: PositionOrderCollection, mo: ModificationOrder): Unit = {
    for {
      obv <- orders.findBy(mo.getModifiedOrderId, mo.getBroker)
      updatedOrder = modifyOrder(mo, obv)
      if orders.removeIf(_.getId == mo.getModifiedOrderId)
    } yield {
      orders.add(updatedOrder)
      matchOrders()
    }
  }

  private[this] def matchOrders(): Unit = {
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
          buyOrders  add b.minusAmount(amountLimit)
          sellOrders add s.minusAmount(amountLimit)
          matchOrders()
        case (true, false) =>
          buyOrders  add b.minusAmount(amountLimit)
          matchOrders()
        case (false, true) =>
          sellOrders add s.minusAmount(amountLimit)
          matchOrders()
        case _ =>
      }
    }
  }
}