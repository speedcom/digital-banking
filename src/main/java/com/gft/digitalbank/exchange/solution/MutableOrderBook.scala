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

  private val transactions = Sets.newHashSet[Transaction]()

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
    modifyOrder(buy, mo)(addBuyOrder)
    modifyOrder(sell, mo)(addSellOrder)
  }

  def getTransactions: JHashSet[Transaction] = transactions

  def getOrderBook = {
    OrderBook.builder()
      .product(product)
      .buyEntries(prepareEntries(buy))
      .sellEntries(prepareEntries(sell))
      .build()
  }

  private def prepareEntries(q: JPriorityQueue[PositionOrder]): JArrayList[OrderEntry] = {
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

  private[this] def addBuyOrder(order: ModificationOrder, old: PositionOrder) = PositionOrder.builder()
    .details(new OrderDetails(
      order.getDetails.getAmount,
      order.getDetails.getPrice))
    .timestamp(order.getTimestamp)
    .product(old.getProduct)
    .broker(order.getBroker)
    .client(old.getClient)
    .side(Side.BUY)
    .id(old.getId)
    .build()

  private[this] def addSellOrder(order: ModificationOrder, old: PositionOrder) =  PositionOrder.builder()
    .details(new OrderDetails(
      order.getDetails.getAmount,
      order.getDetails.getPrice))
    .timestamp(order.getTimestamp)
    .product(old.getProduct)
    .broker(order.getBroker)
    .client(old.getClient)
    .side(Side.SELL)
    .id(old.getId)
    .build()

  private def modifyOrder(pq: JPriorityQueue[PositionOrder], m: ModificationOrder)(buildModifiedOrder: (ModificationOrder, PositionOrder) => PositionOrder): Unit = {
    for {
      obv <- pq.asScala.find(o => o.getId == m.getModifiedOrderId && o.getBroker == m.getBroker)
      modifiedOrder = buildModifiedOrder(m, obv)
      if pq.removeIf(idMatches(m))
    } yield {
      pq.add(modifiedOrder)
      matchTransactions()
    }
  }

  private def minusAmount(order: PositionOrder, minusAmount: Int): PositionOrder = {
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

  private def matchTransactions(): Unit = {
    println("Starting matching")
    for {
      b <- Option(buy.peek())
      s <- Option(sell.peek())
      if b.getDetails.getPrice >= s.getDetails.getPrice
      amountLimit = math.min(b.getDetails.getAmount, s.getDetails.getAmount)
      priceLimit  = if(b.getTimestamp < s.getTimestamp) b.getDetails.getPrice else s.getDetails.getPrice
      transaction = buildTransaction(b, s, amountLimit, priceLimit)
    } yield {
      println(s"[OrderBookActor] Matching: \nbuy-offer: $b \nsell-offer: $s")

      transactions.add(transaction)
      buy.poll()
      sell.poll()

      (b.getDetails.getAmount > amountLimit, s.getDetails.getAmount > amountLimit) match {
        case (true, true) =>
          buy  add minusAmount(b, amountLimit)
          sell add minusAmount(s, amountLimit)
          matchTransactions()
        case (true, false) =>
          buy  add minusAmount(b, amountLimit)
          matchTransactions()
        case (false, true) =>
          sell add minusAmount(s, amountLimit)
          matchTransactions()
        case _ =>
      }
    }
  }

  private def toOrderEntry(order: PositionOrder, id: Int) = {
    OrderEntry.builder()
      .id(id)
      .amount(order.getDetails.getAmount)
      .price(order.getDetails.getPrice)
      .client(order.getClient)
      .broker(order.getBroker)
      .build()
  }

  private def buildTransaction(buy: PositionOrder, sell: PositionOrder, amountLimit: Int, priceLimit: Int) = {
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