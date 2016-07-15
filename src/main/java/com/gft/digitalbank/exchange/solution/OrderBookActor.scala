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
  case object MatchTransactions            extends BookCommand
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
      exchangeActorRef ! ExchangeActor.RecordOrderBook(product, buildOrderBook)
      context.stop(self)
    case BuyOrder(b) =>
      buy enqueue BuyOrderValue(b)
      self ! MatchTransactions
    case SellOrder(s) =>
      sell enqueue SellOrderValue(s)
      self ! MatchTransactions
    case MatchTransactions =>
      for {
        b <- buy .headOption
        s <- sell.headOption
        if b.price >= s.price
        amountLimit = math.max(b.amount, s.amount)
        priceLimit  = if(b.timestamp > s.timestamp) b.price else s.price
        transaction = buildTransaction(b, s, amountLimit, priceLimit)
      } yield {
        transactions.add(transaction)

        buy .dequeue()
        sell.dequeue()

        if(b.amount > amountLimit) {
          buy.enqueue(b.ccopy(amountLimit))
          self ! MatchTransactions
        }

        if(s.amount > amountLimit) {
          sell.enqueue(s.ccopy(amountLimit))
          self ! MatchTransactions
        }
      }
  }

  private def buildOrderBook = {
    OrderBook.builder()
      .product(product)
      .buyEntries(buy.map(b => toOrderEntry(b.order)).asJavaCollection)
      .sellEntries(sell.map(s => toOrderEntry(s.order)).asJavaCollection)
      .build()
  }

  private def toOrderEntry(order: PositionOrder) = {
    OrderEntry.builder()
      .id(order.getId)
      .amount(order.getDetails.getAmount)
      .price(order.getDetails.getPrice)
      .client(order.getClient)
      .broker(order.getBroker)
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