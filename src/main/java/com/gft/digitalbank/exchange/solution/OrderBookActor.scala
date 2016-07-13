package com.gft.digitalbank.exchange.solution

import akka.actor.{Actor, ActorRef}
import com.gft.digitalbank.exchange.model.orders.{PositionOrder, Side}
import com.gft.digitalbank.exchange.model.{OrderBook, Transaction}

class OrderBookActor(exchangeActorRef: ActorRef, product: String) extends Actor {
  import OrderBookActor._

  val buy  = collection.mutable.Buffer.empty[PositionOrder]
  val sell = collection.mutable.Buffer.empty[PositionOrder]

  override def receive: Receive = {
    case x =>
      println(s"BOOK $product got $x")
      x match {
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
          buy.append(b)
          self ! MatchTransactions
        case SellOrder(s) =>
          sell.append(s)
          self ! MatchTransactions
        case MatchTransactions =>
          println(s"Matching $product: $buy $sell")
      }
  }
}

object OrderBookActor {
  sealed trait BookCommand
  case object GetTransactions              extends BookCommand
  case object MatchTransactions            extends BookCommand
  case class BuyOrder(po: PositionOrder)   extends BookCommand
  case class SellOrder(po: PositionOrder)  extends BookCommand
}