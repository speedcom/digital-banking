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
      bestBuyOrder  <- buyOrders.peekOpt
      bestSellOrder <- sellOrders.peekOpt
      if bestBuyOrder.getDetails.getPrice >= bestSellOrder.getDetails.getPrice
      amountLimit = math.min(bestBuyOrder.getDetails.getAmount, bestSellOrder.getDetails.getAmount)
      priceLimit  = if(bestBuyOrder.getTimestamp < bestSellOrder.getTimestamp) bestBuyOrder.getDetails.getPrice else bestSellOrder.getDetails.getPrice
      if transactor.add(bestBuyOrder, bestSellOrder, amountLimit, priceLimit)
    } yield {
      buyOrders.poll()
      sellOrders.poll()

      (bestBuyOrder.getDetails.getAmount > amountLimit, bestSellOrder.getDetails.getAmount > amountLimit) match {
        case (true, true) =>
          buyOrders  add bestBuyOrder.minusAmount(amountLimit)
          sellOrders add bestSellOrder.minusAmount(amountLimit)
          matchOrders()
        case (true, false) =>
          buyOrders  add bestBuyOrder.minusAmount(amountLimit)
          matchOrders()
        case (false, true) =>
          sellOrders add bestSellOrder.minusAmount(amountLimit)
          matchOrders()
        case _ =>
      }
    }
  }
}