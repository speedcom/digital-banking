package com.gft.digitalbank.exchange.solution

import akka.actor.{Actor, ActorRef}
import com.gft.digitalbank.exchange.model.orders.{CancellationOrder, ModificationOrder, PositionOrder}
import com.gft.digitalbank.exchange.solution.orderBook.MutableOrderBook

class OrderBookActor(exchangeActorRef: ActorRef, product: String) extends Actor {
  import OrderBookActor._

  private val orderBook = new MutableOrderBook(product)

  override def receive: Receive = {
    case BuyOrder(b)     => orderBook.handleBuyOrder(b)
    case SellOrder(s)    => orderBook.handleSellOrder(s)
    case CancelOrder(c)  => orderBook.handleCancellationOrder(c)
    case ModifyOrder(m)  => orderBook.handleModificationOrder(m)
    case GetTransactions =>
      exchangeActorRef ! ExchangeActor.RecordTransactions(orderBook.getTransactions.transactions)
      exchangeActorRef ! ExchangeActor.RecordOrderBook(orderBook.getOrderBook)
      context.stop(self)
  }
}

object OrderBookActor {
  sealed trait BookCommand
  case object GetTransactions                   extends BookCommand
  case class BuyOrder(po: PositionOrder)        extends BookCommand
  case class SellOrder(po: PositionOrder)       extends BookCommand
  case class CancelOrder(co: CancellationOrder) extends BookCommand
  case class ModifyOrder(mo: ModificationOrder) extends BookCommand
}