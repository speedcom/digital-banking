package com.gft.digitalbank.exchange.solution

import java.util.{ArrayList => JArrayList, PriorityQueue => JPriorityQueue, HashSet => JHashSet}
import java.util.function.Predicate

import com.gft.digitalbank.exchange.model.{OrderBook, OrderDetails, OrderEntry, Transaction}
import com.gft.digitalbank.exchange.model.orders.{CancellationOrder, ModificationOrder, PositionOrder, Side}
import com.google.common.collect.Sets

import scala.collection.JavaConverters._

class MutableOrderBook(product: String) {

  private val buy  = new JPriorityQueue[OrderBookValue](new BuyOrderComparator)
  private val sell = new JPriorityQueue[OrderBookValue](new SellOrderComparator)

  private val transactions = Sets.newHashSet[Transaction]()

  private[this] def idMatches(co: CancellationOrder) = new Predicate[OrderBookValue] {
    def test(obv: OrderBookValue) = obv.order.getId == co.getCancelledOrderId && obv.order.getBroker == co.getBroker
  }

  private[this] def idMatches(mo: ModificationOrder) = new Predicate[OrderBookValue] {
    def test(obv: OrderBookValue) = obv.order.getId == mo.getModifiedOrderId
  }

  def addBuyOrder(obv: OrderBookValue): Unit = {
    buy.add(obv)
    matchTransactions()
  }

  def addSellOrder(obv: OrderBookValue): Unit = {
    sell.add(obv)
    matchTransactions()
  }

  def cancelOrder(co: CancellationOrder): Unit = {
    buy.removeIf(idMatches(co))
    sell.removeIf(idMatches(co))
  }

  def modifyOrder(mo: ModificationOrder): Unit = {
    modifyOrder(buy, mo)(addBuyOrder)
    modifyOrder(sell, mo)(addSellOrder)
  }

  def getTransactions: JHashSet[Transaction] = transactions

  def getOrderBook: OrderBook = {

    def prepareEntries(q: JPriorityQueue[OrderBookValue]): JArrayList[OrderEntry] = {
      val entries = new JArrayList[OrderEntry]
      var id = 1

      while(!q.isEmpty) {
        entries.add(toOrderEntry(q.poll().order, id))
        id += 1
      }

      entries
    }

    OrderBook.builder()
      .product(product)
      .buyEntries(prepareEntries(buy))
      .sellEntries(prepareEntries(sell))
      .build()
  }



  private[this] def addBuyOrder(order: ModificationOrder, old: OrderBookValue) = PositionOrder.builder()
    .details(new OrderDetails(
      order.getDetails.getAmount,
      order.getDetails.getPrice))
    .timestamp(order.getTimestamp)
    .product(old.order.getProduct)
    .broker(order.getBroker)
    .client(old.order.getClient)
    .side(Side.BUY)
    .id(old.order.getId)
    .build()

  private[this] def addSellOrder(order: ModificationOrder, old: OrderBookValue) =  PositionOrder.builder()
    .details(new OrderDetails(
      order.getDetails.getAmount,
      order.getDetails.getPrice))
    .timestamp(order.getTimestamp)
    .product(old.order.getProduct)
    .broker(order.getBroker)
    .client(old.order.getClient)
    .side(Side.SELL)
    .id(old.order.getId)
    .build()

  private def modifyOrder(pq: JPriorityQueue[OrderBookValue], m: ModificationOrder)(buildModifiedOrder: (ModificationOrder, OrderBookValue) => PositionOrder): Unit = {
    for {
      obv <- pq.asScala.find(o => o.order.getId == m.getModifiedOrderId && o.order.getBroker == m.getBroker)
      mo  = buildModifiedOrder(m, obv)
      if pq.removeIf(idMatches(m))
    } yield {
      pq.add(OrderBookValue(mo, true))
      matchTransactions()
    }
  }

  private def matchTransactions(): Unit = {
    println("Starting matching")
    for {
      b <- Option(buy.peek())
      s <- Option(sell.peek())
      if b.price >= s.price
      amountLimit = math.min(b.amount, s.amount)
      priceLimit  = if(b.timestamp < s.timestamp) b.price else s.price
      transaction = buildTransaction(b, s, amountLimit, priceLimit)
    } yield {
      println(s"[OrderBookActor] Matching: \nbuy-offer: $b \nsell-offer: $s")

      transactions.add(transaction)
      buy.poll()
      sell.poll()

      (b.amount > amountLimit, s.amount > amountLimit) match {
        case (true, true) =>
          buy  add b.update(amountLimit)
          sell add s.update(amountLimit)
          matchTransactions()
        case (true, false) =>
          buy  add b.update(amountLimit)
          matchTransactions()
        case (false, true) =>
          sell add s.update(amountLimit)
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

  private def buildTransaction(buy: OrderBookValue, sell: OrderBookValue, amountLimit: Int, priceLimit: Int) = {
    Transaction.builder()
      .id(transactions.size() + 1)
      .amount(amountLimit)
      .price(priceLimit)
      .brokerBuy(buy.order.getBroker)
      .brokerSell(sell.order.getBroker)
      .clientBuy(buy.order.getClient)
      .clientSell(sell.order.getClient)
      .product(product)
      .build()
  }

}