package com.gft.digitalbank.exchange.solution

import akka.actor.Actor

case object GetTransactions

class BookActor(val product: String) extends Actor {

  override def receive: Receive = {
    case x =>
      println(s"$product recevied $x")
  }
}
