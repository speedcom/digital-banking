package com.gft.digitalbank.exchange.solution

import akka.actor.{ Actor, ActorRef }
import com.gft.digitalbank.exchange.model.{ OrderBook, Transaction }

case object GetTransactions
case object MatchTransactions

class BookActor(exchangeActorRef: ActorRef, product: String) extends Actor {

  val buy  = collection.mutable.Buffer.empty[Buy]
  val sell = collection.mutable.Buffer.empty[Sell]

  override def receive: Receive = {
    case GetTransactions =>
      exchangeActorRef ! RecordOrderBook(product, OrderBook.builder().product(product).build())
      context.stop(self)
    case b: Buy =>
      buy.append(b)
      self ! MatchTransactions
    case s: Sell =>
      sell.append(s)
      self ! MatchTransactions

    case MatchTransactions =>
      exchangeActorRef ! RecordTransaction(
          product,
          Transaction.builder().id(1).amount(100).price(100).product(product).brokerBuy("1").brokerSell("2").clientBuy("100").clientSell("101").build()
      )
    case x =>
      println(s"$product recevied $x")
  }
}
