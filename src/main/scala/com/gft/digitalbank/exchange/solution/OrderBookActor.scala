package com.gft.digitalbank.exchange.solution

import akka.actor.{Actor, ActorRef, Props}
import com.gft.digitalbank.exchange.model.orders.{CancellationOrder, ModificationOrder, PositionOrder}
import com.gft.digitalbank.exchange.solution.orderBook.MutableOrderBook

class OrderBookActor(exchangeActorRef: ActorRef, product: OrderBookProduct) extends Actor {
  import OrderBookActor._

  private[this] val orderBook = new MutableOrderBook(product)

  override def receive: Receive = {
    case BuyOrder(b)    => orderBook.handleBuyOrder(b)
    case SellOrder(s)   => orderBook.handleSellOrder(s)
    case CancelOrder(c) => orderBook.handleCancellationOrder(c)
    case ModifyOrder(m) => orderBook.handleModificationOrder(m)
    case GetResults =>
      exchangeActorRef ! ExchangeActor.RecordTransactions(orderBook.getTransactions)
      exchangeActorRef ! ExchangeActor.RecordOrderBook(orderBook.getOrderBook)
      context.stop(self)
  }
}

object OrderBookActor {

  def props(exchangeActorRef: ActorRef, product: OrderBookProduct): Props = {
    Props(new OrderBookActor(exchangeActorRef, product))
  }

  sealed trait BookCommand
  case object GetResults                        extends BookCommand
  case class BuyOrder(po: PositionOrder)        extends BookCommand
  case class SellOrder(po: PositionOrder)       extends BookCommand
  case class CancelOrder(co: CancellationOrder) extends BookCommand
  case class ModifyOrder(mo: ModificationOrder) extends BookCommand
}

case class OrderBookProduct(product: String) extends AnyVal
