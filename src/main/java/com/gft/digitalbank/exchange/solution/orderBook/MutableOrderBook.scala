package com.gft.digitalbank.exchange.solution.orderBook

import com.gft.digitalbank.exchange.model.orders.{CancellationOrder, ModificationOrder, PositionOrder}
import com.gft.digitalbank.exchange.model.{OrderBook, OrderDetails}
import PositionOrderOps._

class MutableOrderBook(product: String) {

  private val buyOrders  = new BuyOrders
  private val sellOrders = new SellOrders
  private val transactor = new OrderBookTransactor(product)
  private val matcher    = new OrdersMatcher(buyOrders, sellOrders)

  def getTransactions: Transactions = transactor.getTransactions

  def getOrderBook: OrderBook = new OrderBookPreparator(product).prepare(buyOrders, sellOrders)

  def handleBuyOrder(po: PositionOrder): Unit = {
    buyOrders.add(po)
    runOrderBook()
  }

  def handleSellOrder(po: PositionOrder): Unit = {
    sellOrders.add(po)
    runOrderBook()
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
      runOrderBook()
    }
  }

  private[this] def runOrderBook(): Unit = {
    for {
      matched <- matcher.matchOrders()
      if transactor.add(matched.bestBuyOffer, matched.bestSellOffer, matched.amountLimit, matched.priceLimit)
      _ <- Option(buyOrders.poll())
      _ <- Option(sellOrders.poll())
    } yield {
      val hasBuyEnoughAmount  = matched.bestBuyOffer.getDetails.getAmount  > matched.amountLimit.amount
      val hasSellEnoughAmount = matched.bestSellOffer.getDetails.getAmount > matched.amountLimit.amount

      def addBuyOrderWithRestAmount  = buyOrders.add(matched.bestBuyOffer.minusAmount(matched.amountLimit))
      def addSellOrderWithRestAmount = sellOrders.add(matched.bestSellOffer.minusAmount(matched.amountLimit))

      if(hasBuyEnoughAmount && hasSellEnoughAmount) {
        addBuyOrderWithRestAmount
        addSellOrderWithRestAmount
        runOrderBook()
      }
      else if(hasBuyEnoughAmount) {
        addBuyOrderWithRestAmount
        runOrderBook()
      }
      else if(hasSellEnoughAmount) {
        addSellOrderWithRestAmount
        runOrderBook()
      }
    }
  }
}