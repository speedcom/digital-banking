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
      priceLimit  = if(b.timestamp > s.timestamp) b.price else s.price
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

  private def sendNonEmptyOrderBook(ob: OrderBook)(send: Option[OrderBook] => Unit): Unit = {
    println(s"[OrderBookActor] invooking sendNonEmptyOrderBook method with order-book: $ob")
    if(!(ob.getSellEntries.isEmpty && ob.getBuyEntries.isEmpty))
      send(Some(ob))
    else
      send(None)
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

    OrderBook.builder()
      .product(product)
      .buyEntries (buy .view.zipWithIndex.map { case (b, id) => toOrderEntry(b.order, id+1) }.asJavaCollection)
      .sellEntries(sell.view.zipWithIndex.map { case (s, id) => toOrderEntry(s.order, id+1) }.asJavaCollection)
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

sealed trait OrderValue {
  def order: PositionOrder

  val price: Int      = order.getDetails.getPrice
  val amount: Int     = order.getDetails.getAmount
  val timestamp: Long = order.getTimestamp

  val partiallyExecuted: Boolean

  protected def update(decreaseByAmountLimit: Int): PositionOrder = {
    PositionOrder.builder()
      .details(new OrderDetails(order.getDetails.getAmount - decreaseByAmountLimit, order.getDetails.getPrice))
      .timestamp(order.getTimestamp)
      .product(order.getProduct)
      .broker(order.getBroker)
      .client(order.getClient)
      .side(order.getSide)
      .id(order.getId)
      .build()
  }
}
case class SellOrderValue(order: PositionOrder, partiallyExecuted: Boolean = false) extends OrderValue {
  def ccopy(decreaseByAmountLimit: Int) = this.copy(order = update(decreaseByAmountLimit), true)
}
case class BuyOrderValue(order: PositionOrder, partiallyExecuted: Boolean = false) extends OrderValue {
  def ccopy(decreaseByAmountLimit: Int) = this.copy(order = update(decreaseByAmountLimit), true)
}

object BuyOrderValue {
  val priceDescTimestampAscOrdering = Ordering.by[BuyOrderValue, (Int, Long)] {
    case buy => (buy.order.getDetails.getPrice, -1 * buy.order.getTimestamp)
  }
}

object SellOrderValue {
  val priceAscTimestampAscOrdering = Ordering.by[SellOrderValue, (Int, Long)] {
    case sell => (-1 * sell.order.getDetails.getPrice, -1 * sell.order.getTimestamp)
  }
}