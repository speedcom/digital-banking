package com.gft.digitalbank.exchange.solution

import akka.actor.{Actor, ActorRef}
import com.gft.digitalbank.exchange.model.orders.PositionOrder
import com.gft.digitalbank.exchange.model.{OrderBook, Transaction}

case object GetTransactions
case object MatchTransactions

class BookActor(exchangeActorRef: ActorRef, product: String) extends Actor {

  val buy  = collection.mutable.Buffer.empty[PositionOrder]
  val sell = collection.mutable.Buffer.empty[PositionOrder]

  override def receive: Receive = {
    case x =>
      println(s"BOOK $product got $x")
      x match {
        case GetTransactions =>
          println(s"Processing finished, send not matched orders")

          //Transactions should be sent immediately when they are matched
          exchangeActorRef ! RecordTransaction(
              product,
              Transaction.builder().id(1).amount(100).price(100).product(product).brokerBuy("1").brokerSell("2").clientBuy("100").clientSell("101").build()
          )

          exchangeActorRef ! RecordOrderBook(product, OrderBook.builder().product(product).build())

          context.stop(self)
        case b: PositionOrder => // TODO: BUY
          buy.append(b)
          self ! MatchTransactions
        case s: PositionOrder => // TODO: SELL
          sell.append(s)
          self ! MatchTransactions

        case MatchTransactions =>
          println(s"Matching $product: $buy $sell")
      }
  }
}
