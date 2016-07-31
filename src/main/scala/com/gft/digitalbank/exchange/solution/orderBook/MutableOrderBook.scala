package com.gft.digitalbank.exchange.solution.orderBook

import com.gft.digitalbank.exchange.model.orders.{CancellationOrder, ModificationOrder, PositionOrder}
import com.gft.digitalbank.exchange.model.{OrderBook, OrderDetails}
import PositionOrderOps._

class MutableOrderBook(product: String) {

  private[this] val buyOrders  = new BuyOrders
  private[this] val sellOrders = new SellOrders
  private[this] val transactor = new OrderBookTransactor(product)
  private[this] val summary    = new OrderBookSummary(product)
  private[this] val matcher    = new OrdersMatcher(buyOrders, sellOrders)

  def getTransactions: Transactions = transactor.getTransactions

  def getOrderBook: OrderBook = summary.prepare(buyOrders, sellOrders)

  def handleBuyOrder(po: PositionOrder): Unit = runOrderBookAfter { buyOrders.add(po) }

  def handleSellOrder(po: PositionOrder): Unit = runOrderBookAfter { sellOrders.add(po) }

  def handleCancellationOrder(co: CancellationOrder): Unit = {
    val idAndBrokerSame = (po: PositionOrder) => po.getId == co.getCancelledOrderId && po.getBroker == co.getBroker
    buyOrders.removeIf(idAndBrokerSame)
    sellOrders.removeIf(idAndBrokerSame)
  }

  def handleModificationOrder(mo: ModificationOrder): Unit = {
    def modifyOrder(orders: PositionOrderCollection, mo: ModificationOrder): Unit = {
      for {
        order <- orders.findBy(mo.getModifiedOrderId, mo.getBroker)
        if orders.removeIf(_.getId == mo.getModifiedOrderId)
      } yield {
        runOrderBookAfter { orders.add(order.updateVia(mo)) }
      }
    }

    modifyOrder(buyOrders, mo)
    modifyOrder(sellOrders, mo)
  }

  private[this] def runOrderBook(): Unit = {
    for {
      matched <- matcher.matchOrders()
      if transactor.addTransaction(matched.bestBuyOffer, matched.bestSellOffer, matched.amountLimit, matched.priceLimit)
      _ <- Option(buyOrders.poll())
      _ <- Option(sellOrders.poll())
    } yield {
      val hasBuyEnoughAmount  = matched.bestBuyOffer.getDetails.getAmount  > matched.amountLimit.amount
      val hasSellEnoughAmount = matched.bestSellOffer.getDetails.getAmount > matched.amountLimit.amount

      def addBuyOrderWithRestAmount()  = buyOrders.add(matched.bestBuyOffer.minusAmount(matched.amountLimit))
      def addSellOrderWithRestAmount() = sellOrders.add(matched.bestSellOffer.minusAmount(matched.amountLimit))

      runOrderBookAfter {
        if(hasBuyEnoughAmount && hasSellEnoughAmount) {
          addBuyOrderWithRestAmount()
          addSellOrderWithRestAmount()
        }
        else if(hasBuyEnoughAmount) {
          addBuyOrderWithRestAmount()
        }
        else if(hasSellEnoughAmount) {
          addSellOrderWithRestAmount()
        }
      }
    }
  }

  private[this] def runOrderBookAfter(computation: => Unit): Unit = {
    computation
    runOrderBook()
  }
}