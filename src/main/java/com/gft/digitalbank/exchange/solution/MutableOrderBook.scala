package com.gft.digitalbank.exchange.solution

import java.util.{Comparator, ArrayList => JArrayList, HashSet => JHashSet, PriorityQueue => JPriorityQueue}
import java.util.function.Predicate

import com.gft.digitalbank.exchange.model.{OrderBook, OrderDetails, OrderEntry, Transaction}
import com.gft.digitalbank.exchange.model.orders.{CancellationOrder, ModificationOrder, PositionOrder}
import com.google.common.collect.Sets

import scala.collection.JavaConverters._

class MutableOrderBook(product: String) {

  private val buyOrders  = new BuyOrders
  private val sellOrders = new SellOrders
  private val transactor = new OrderBookTransactor(product)

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

  def getTransactions: JHashSet[Transaction] = transactor.getTransactions

  def getOrderBook: OrderBook = new OrderBookPreparator().prepare

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

  private class OrderBookPreparator {

    def prepare: OrderBook = {
      OrderBook.builder()
        .product(product)
        .buyEntries(prepareEntries(buyOrders))
        .sellEntries(prepareEntries(sellOrders))
        .build()
    }

    private[this] def prepareEntries(orders: PositionOrderCollection): JArrayList[OrderEntry] = {
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
}

final class OrderBookTransactor(product: String) {

  private val transactions = Sets.newHashSet[Transaction]()

  def getTransactions: JHashSet[Transaction] = transactions

  def add(buy: PositionOrder, sell: PositionOrder, amountLimit: Int, priceLimit: Int): Unit = {
    val t = buildTransaction(buy, sell, amountLimit, priceLimit)
    transactions.add(t)
  }

  private[this] def buildTransaction(buy: PositionOrder, sell: PositionOrder, amountLimit: Int, priceLimit: Int) = {
    Transaction.builder()
      .id(transactions.size() + 1)
      .amount(amountLimit)
      .price(priceLimit)
      .brokerBuy(buy.getBroker)
      .brokerSell(sell.getBroker)
      .clientBuy(buy.getClient)
      .clientSell(sell.getClient)
      .product(product)
      .build()
  }
}

sealed abstract class PositionOrderCollection(comparator: Comparator[PositionOrder]) {

  private val orders: JPriorityQueue[PositionOrder] = new JPriorityQueue[PositionOrder](comparator)

  def add(po: PositionOrder): Unit = orders.add(po)

  def removeIf(predicate: Predicate[PositionOrder]): Boolean = orders.removeIf(predicate)

  def peekOpt: Option[PositionOrder] = Option(orders.peek())

  def poll(): PositionOrder = orders.poll()

  def findBy(modifiedOrderId: Int, broker: String): Option[PositionOrder] = {
    orders.asScala.find(o => o.getId == modifiedOrderId && o.getBroker == broker)
  }

  def isEmpty: Boolean  = orders.isEmpty
  def nonEmpty: Boolean = !isEmpty
}

final class BuyOrders  extends PositionOrderCollection(new BuyOrderComparator)
final class SellOrders extends PositionOrderCollection(new SellOrderComparator)

final class BuyOrderComparator extends Comparator[PositionOrder] {

  private val buyOrdering = Ordering.by[PositionOrder, (Int, Long)] { buy =>
    (buy.getDetails.getPrice * -1, buy.getTimestamp)
  }

  override def compare(o1: PositionOrder, o2: PositionOrder): Int = {
    buyOrdering.compare(o1, o2)
  }
}

final class SellOrderComparator extends Comparator[PositionOrder] {

  private val sellOrdering = Ordering.by[PositionOrder, (Int, Long)] { sell =>
    (sell.getDetails.getPrice, sell.getTimestamp)
  }

  override def compare(o1: PositionOrder, o2: PositionOrder): Int = {
    sellOrdering.compare(o1, o2)
  }
}