package com.gft.digitalbank.exchange.solution

import akka.actor.{Actor, ActorRef}
import com.gft.digitalbank.exchange.model.orders.PositionOrder
import com.gft.digitalbank.exchange.model.{OrderBook, OrderDetails, OrderEntry, Transaction}
import com.google.common.collect.Sets

import scala.collection.mutable
import scala.collection.JavaConverters._

object OrderBookActor {
  sealed trait BookCommand
  case object GetTransactions              extends BookCommand
  case class BuyOrder(po: PositionOrder)   extends BookCommand
  case class SellOrder(po: PositionOrder)  extends BookCommand
}

class OrderBookActor(exchangeActorRef: ActorRef, product: String) extends Actor {
  import OrderBookActor._

  private val buy  = new mutable.PriorityQueue[BuyOrderValue]()(BuyOrderValue.priceDescTimestampAscOrdering)
  private val sell = new mutable.PriorityQueue[SellOrderValue]()(SellOrderValue.priceAscTimestampAscOrdering)

  private val transactions = Sets.newHashSet[Transaction]()

  override def receive: Receive = {
    case GetTransactions =>
      exchangeActorRef ! ExchangeActor.RecordTransactions(transactions)
      exchangeActorRef ! ExchangeActor.RecordOrderBook(buildOrderBook)
      context.stop(self)
    case BuyOrder(b) =>
      println(s"[OrderBookActor] BuyOrder: $b")
      buy enqueue BuyOrderValue(b)
      matchTransactions()
    case SellOrder(s) =>
      println(s"[OrderBookActor] SellOrder: $s")
      sell enqueue SellOrderValue(s)
      matchTransactions()
  }

  private def matchTransactions(): Unit = {
    println("Starting matching")
    for {
      b <- buy .headOption
      s <- sell.headOption
      if b.price >= s.price
      amountLimit = math.min(b.amount, s.amount)
      priceLimit  = if(b.timestamp < s.timestamp) b.price else s.price
      transaction = buildTransaction(b, s, amountLimit, priceLimit)
    } yield {
      println(s"[OrderBookActor] MatchTransactions, matching \nbuy-offer: $b \nsell-offer: $s")
      println(s"[OrderBookActor] MatchTransactions, transactions-set before addition: $transactions")
      transactions.add(transaction)
      println(s"[OrderBookActor] MatchTransactions, transactions-set after: $transactions")

      println(s"s[OrderBookActor] MatchTransactions, buy-set before dequeue operation: $buy")
      buy.dequeue()
      println(s"s[OrderBookActor] MatchTransactions, buy-set after dequeue operation: $buy")

      println(s"s[OrderBookActor] MatchTransactions, sell-set before dequeue operation: $sell")
      sell.dequeue()
      println(s"s[OrderBookActor] MatchTransactions, sell-set after dequeue operation: $sell")

      (b.amount > amountLimit, s.amount > amountLimit) match {
        case (true, true) =>
          buy  enqueue b.ccopy(amountLimit)
          sell enqueue s.ccopy(amountLimit)
          matchTransactions()
        case (true, false) =>
          buy  enqueue b.ccopy(amountLimit)
          matchTransactions()
        case (false, true) =>
          sell enqueue s.ccopy(amountLimit)
          matchTransactions()
        case _ =>
      }
    }
  }

  private def buildOrderBook = {

    def toOrderEntry(order: PositionOrder, id: Int) = {
      OrderEntry.builder()
        .id(id)
        .amount(order.getDetails.getAmount)
        .price(order.getDetails.getPrice)
        .client(order.getClient)
        .broker(order.getBroker)
        .build()
    }

    def takeBuyOrdersSorted = {
      val buffer = mutable.Buffer[BuyOrderValue]()
      while(buy.nonEmpty) {
        buffer.append(buy.dequeue())
      }
      buffer
    }

    def takeSellOrdersSorted = {
      val buffer = mutable.Buffer[SellOrderValue]()
      while(sell.nonEmpty) {
        buffer.append(sell.dequeue())
      }
      buffer
    }

    OrderBook.builder()
      .product(product)
      .buyEntries (takeBuyOrdersSorted.iterator.zipWithIndex.map { case (b, id) => toOrderEntry(b.order, id+1) }.toList.asJavaCollection) // TODO: is that perf enough? (dont want to create intermediate collection)
      .sellEntries(takeSellOrdersSorted.iterator.zipWithIndex.map { case (s, id) => toOrderEntry(s.order, id+1) }.toList.asJavaCollection) // TODO: is that perf enough? (dont want to create intermediate collection)
      .build()
  }

  private def buildTransaction(b: BuyOrderValue, s: SellOrderValue, amountLimit: Int, priceLimit: Int) = {
    Transaction.builder()
      .id(transactions.size() + 1)
      .amount(amountLimit)
      .price(priceLimit)
      .brokerBuy(b.order.getBroker)
      .brokerSell(s.order.getBroker)
      .clientBuy(b.order.getClient)
      .clientSell(s.order.getClient)
      .product(product)
      .build()
  }
}