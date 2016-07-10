package com.gft.digitalbank.exchange.solution

import akka.actor.{ Actor, ActorRef }
import com.gft.digitalbank.exchange.model.OrderBook

case object GetTransactions

class BookActor(exchangeActorRef: ActorRef, product: String) extends Actor {
  override def receive: Receive = {
    case GetTransactions =>
      exchangeActorRef ! RecordOrderBook(product, OrderBook.builder().product(product).build())
      context.stop(self)
    case x =>
      println(s"$product recevied $x")
  }
}
