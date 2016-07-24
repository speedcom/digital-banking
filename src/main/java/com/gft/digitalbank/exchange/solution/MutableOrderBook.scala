package com.gft.digitalbank.exchange.solution

import java.util.{Comparator, ArrayList => JArrayList, HashSet => JHashSet, PriorityQueue => JPriorityQueue}
import java.util.function.Predicate

import com.gft.digitalbank.exchange.model.{OrderBook, OrderDetails, OrderEntry, Transaction}
import com.gft.digitalbank.exchange.model.orders.{CancellationOrder, ModificationOrder, PositionOrder, Side}
import com.google.common.collect.Sets

import scala.collection.JavaConverters._

class MutableOrderBook(product: String) {

  private val buy  = new JPriorityQueue[PositionOrder](new BuyOrderComparator)
  private val sell = new JPriorityQueue[PositionOrder](new SellOrderComparator)

  private val transactor = new OrderBookTransactor(product)

  def handleBuyOrder(po: PositionOrder): Unit = {
    buy.add(po)
    matchTransactions()
  }

  def handleSellOrder(po: PositionOrder): Unit = {
    sell.add(po)
    matchTransactions()
  }

  def handleCancellationOrder(co: CancellationOrder): Unit = {
    buy.removeIf(idMatches(co))
    sell.removeIf(idMatches(co))
  }

  def handleModificationOrder(mo: ModificationOrder): Unit = {
    modifyOrder(buy, mo)(modifyOrder)
    modifyOrder(sell, mo)(modifyOrder)
  }

  def getTransactions: JHashSet[Transaction] = {
    transactor.getTransactions
  }

  def getOrderBook = {
    OrderBook.builder()
      .product(product)
      .buyEntries(prepareEntries(buy))
      .sellEntries(prepareEntries(sell))
      .build()
  }

  private[this] def prepareEntries(q: JPriorityQueue[PositionOrder]): JArrayList[OrderEntry] = {
    val entries = new JArrayList[OrderEntry]
    var id = 1

    while(!q.isEmpty) {
      entries.add(toOrderEntry(q.poll(), id))
      id += 1
    }
    entries
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

  private[this] def modifyOrder(pq: JPriorityQueue[PositionOrder], m: ModificationOrder)(buildModifiedOrder: (ModificationOrder, PositionOrder) => PositionOrder): Unit = {
    for {
      obv <- pq.asScala.find(o => o.getId == m.getModifiedOrderId && o.getBroker == m.getBroker)
      modifiedOrder = buildModifiedOrder(m, obv)
      if pq.removeIf(idMatches(m))
    } yield {
      pq.add(modifiedOrder)
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
      b <- Option(buy.peek())
      s <- Option(sell.peek())
      if b.getDetails.getPrice >= s.getDetails.getPrice
      amountLimit = math.min(b.getDetails.getAmount, s.getDetails.getAmount)
      priceLimit  = if(b.getTimestamp < s.getTimestamp) b.getDetails.getPrice else s.getDetails.getPrice
    } yield {
      transactor.add(b, s, amountLimit, priceLimit)
      buy.poll()
      sell.poll()

      (b.getDetails.getAmount > amountLimit, s.getDetails.getAmount > amountLimit) match {
        case (true, true) =>
          buy  add orderMinusAmount(b, amountLimit)
          sell add orderMinusAmount(s, amountLimit)
          matchTransactions()
        case (true, false) =>
          buy  add orderMinusAmount(b, amountLimit)
          matchTransactions()
        case (false, true) =>
          sell add orderMinusAmount(s, amountLimit)
          matchTransactions()
        case _ =>
      }
    }
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

final class OrderBookTransactor(product: String) {

  private val transactions = Sets.newHashSet[Transaction]()

  def getTransactions = transactions

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