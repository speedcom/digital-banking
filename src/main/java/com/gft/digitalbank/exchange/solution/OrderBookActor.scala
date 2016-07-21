package com.gft.digitalbank.exchange.solution

import akka.actor.{Actor, ActorRef}
import com.gft.digitalbank.exchange.model.OrderDetails
import com.gft.digitalbank.exchange.model.orders.{ CancellationOrder, PositionOrder, ModificationOrder, Side }
import com.gft.digitalbank.exchange.model.{OrderBook, OrderEntry, Transaction}
import com.google.common.collect.Sets
import java.util.function.Predicate

import java.util.{PriorityQueue => JPriorityQueue, ArrayList => JArrayList}

object OrderBookActor {
  sealed trait BookCommand
  case object GetTransactions              extends BookCommand
  case class BuyOrder(po: PositionOrder)   extends BookCommand
  case class SellOrder(po: PositionOrder)  extends BookCommand
  case class CancelOrder(co: CancellationOrder) extends BookCommand
  case class ModifyOrder(mo: ModificationOrder) extends BookCommand
}

class OrderBookActor(exchangeActorRef: ActorRef, product: String) extends Actor {
  import OrderBookActor._

  private val buy  = new JPriorityQueue[OrderBookValue](new BuyOrderComparator)
  private val sell = new JPriorityQueue[OrderBookValue](new SellOrderComparator)

  private val transactions = Sets.newHashSet[Transaction]()

  private[this] def idMatches(co: CancellationOrder) = new Predicate[OrderBookValue] {
    def test(obv: OrderBookValue) = obv.order.getId == co.getCancelledOrderId
  }

  private[this] def idMatches(mo: ModificationOrder) = new Predicate[OrderBookValue] {
    def test(obv: OrderBookValue) = obv.order.getId == mo.getModifiedOrderId
  }

  private[this] def addBuyOrder(order: ModificationOrder) = PositionOrder.builder()
      .details(new OrderDetails(
        order.getDetails.getAmount,
        order.getDetails.getPrice))
      .timestamp(order.getTimestamp)
      //.product(order.getProduct)
      .broker(order.getBroker)
      //.client(order.getClient)
      .side(Side.BUY)
      .id(order.getId)
      .build()

  private[this] def addSellOrder(order: ModificationOrder) =  PositionOrder.builder()
      .details(new OrderDetails(
        order.getDetails.getAmount,
        order.getDetails.getPrice))
      .timestamp(order.getTimestamp)
      //.product(order.getProduct)
      .broker(order.getBroker)
      //.client(order.getClient)
      .side(Side.SELL)
      .id(order.getId)
      .build()

  override def receive: Receive = {
    case GetTransactions =>
      exchangeActorRef ! ExchangeActor.RecordTransactions(transactions)
      exchangeActorRef ! ExchangeActor.RecordOrderBook(buildOrderBook)
      context.stop(self)
    case BuyOrder(b) =>
      println(s"[OrderBookActor] BuyOrder: $b")
      buy.add(OrderBookValue(b))
      matchTransactions()
    case SellOrder(s) =>
      println(s"[OrderBookActor] SellOrder: $s")
      sell.add(OrderBookValue(s))
      matchTransactions()
    case CancelOrder(c) =>
      println(s"[OrderBookActor] CancelOrder: $c")
      // check if buy or sell orders contain order with id
      buy.removeIf(idMatches(c))
      sell.removeIf(idMatches(c))
    case ModifyOrder(m) =>
      println(s"[OrderBookActor] ModifyOrder: $m")
      if(buy.removeIf(idMatches(m))) addBuyOrder(m)
      if(sell.removeIf(idMatches(m))) addSellOrder(m)

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

  private def buildOrderBook = {

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
