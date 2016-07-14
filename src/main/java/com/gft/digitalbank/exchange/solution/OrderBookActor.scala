package com.gft.digitalbank.exchange.solution

import akka.actor.{Actor, ActorRef}
import com.gft.digitalbank.exchange.model.orders.PositionOrder
import com.gft.digitalbank.exchange.model.{OrderBook, Transaction}

import scala.collection.mutable

class OrderBookActor(exchangeActorRef: ActorRef, product: String) extends Actor {
  import OrderBookActor._

  private val buy  = new mutable.PriorityQueue[BuyOrderValue]()(BuyOrderValue.priceDescTimestampAscOrdering)
  private val sell = new mutable.PriorityQueue[SellOrderValue]()(SellOrderValue.priceAscTimestampAscOrdering)

  override def receive: Receive = {
    case GetTransactions =>
      println(s"Processing finished, send not matched orders")

      //Transactions should be sent immediately when they are matched
      exchangeActorRef ! ExchangeActor.RecordTransaction(
        product,
        Transaction.builder().id(1).amount(100).price(100).product(product).brokerBuy("1").brokerSell("2").clientBuy("100").clientSell("101").build()
      )

      exchangeActorRef ! ExchangeActor.RecordOrderBook(product, OrderBook.builder().product(product).build())

      context.stop(self)
    case BuyOrder(b) =>
      buy enqueue BuyOrderValue(b)
      self ! MatchTransactions
    case SellOrder(s) =>
      sell enqueue SellOrderValue(s)
      self ! MatchTransactions
    case MatchTransactions =>
      println(s"Matching $product: $buy $sell")
  }
}


object OrderBookActor {
  sealed trait BookCommand
  case object GetTransactions              extends BookCommand
  case object MatchTransactions            extends BookCommand
  case class BuyOrder(po: PositionOrder)   extends BookCommand
  case class SellOrder(po: PositionOrder)  extends BookCommand
}

case class SellOrderValue(order: PositionOrder) extends AnyVal
case class BuyOrderValue(order: PositionOrder)  extends AnyVal

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