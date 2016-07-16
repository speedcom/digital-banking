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

  private val buy  = new mutable.PriorityQueue[OrderBookValue]()(OrderBookValue.buyOrdering)
  private val sell = new mutable.PriorityQueue[OrderBookValue]()(OrderBookValue.sellOrdering)

  private val transactions = Sets.newHashSet[Transaction]()

  override def receive: Receive = {
    case GetTransactions =>
      exchangeActorRef ! ExchangeActor.RecordTransactions(transactions)
      exchangeActorRef ! ExchangeActor.RecordOrderBook(buildOrderBook)
      context.stop(self)
    case BuyOrder(b) =>
      println(s"[OrderBookActor] BuyOrder: $b")
      buy enqueue OrderBookValue(b)
      matchTransactions()
    case SellOrder(s) =>
      println(s"[OrderBookActor] SellOrder: $s")
      sell enqueue OrderBookValue(s)
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
      println(s"[OrderBookActor] Matching: \nbuy-offer: $b \nsell-offer: $s")

      transactions.add(transaction)
      buy.dequeue()
      sell.dequeue()

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

    def prepareEntries(q: mutable.PriorityQueue[OrderBookValue]): mutable.Buffer[OrderEntry] = {
      val buffer = mutable.Buffer[OrderBookValue]()
      while(q.nonEmpty) { buffer.append(q.dequeue()) }
      buffer.iterator
        .zipWithIndex
        .map { case (b, id) => toOrderEntry(b.order, id + 1) }
        .toBuffer
    }

    OrderBook.builder()
      .product(product)
      .buyEntries(prepareEntries(buy).asJavaCollection)
      .sellEntries(prepareEntries(sell).asJavaCollection)
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