package com.gft.digitalbank.exchange.solution

import java.util

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.gft.digitalbank.exchange.listener.ProcessingListener
import com.gft.digitalbank.exchange.model.orders.{CancellationOrder, ModificationOrder, PositionOrder, Side}
import com.gft.digitalbank.exchange.model.{OrderBook, SolutionResult, Transaction}
import com.google.common.collect.Sets

import scala.collection.JavaConverters._
import scala.collection.mutable

class ExchangeActor extends Actor with ActorLogging {
  import ExchangeActor._

  override def receive: Receive = idle(Data())

  private def idle(data: Data): Receive = {
    case Register(listener)  =>
      val updatedData = data.copy(processingListener = Some(listener))
      context.become(idle(updatedData))
    case Brokers(newBrokers) =>
      val updatedData = data.copy(activeBrokers = mutable.Set(newBrokers.toArray:_*))
      context.become(idle(updatedData))
    case Start =>
      context.become(active(data))
  }

  private def active(data: Data): Receive = {
    case ProcessPositionOrder(po) =>
      val bookActor = bookActorRef(data, po.getProduct)
      if(po.getSide == Side.BUY)
        bookActor ! OrderBookActor.BuyOrder(po)
      else
        bookActor ! OrderBookActor.SellOrder(po)

    case ProcessModificationOrder(mo) =>
      data.books.values.foreach (_ ! OrderBookActor.ModifyOrder(mo))

    case ProcessCancellationOrder(co) =>
      data.books.values.foreach (_ ! OrderBookActor.CancelOrder(co))

    case BrokerStopped(broker) =>
      data.activeBrokers -= broker
      if (data.activeBrokers.isEmpty)
        gatherResults(data)

    case RecordTransactions(ts) =>
      data.transactions.addAll(ts)

    case RecordOrderBook(orderBook) =>
      data.orderBooks += orderBook
      if (data.orderBooks.size == data.books.size) {
        context.system.terminate()
        sendSummaryToListener(data)
      }
  }

  private[this] def sendSummaryToListener(data: Data) = {

    val isEmpty: OrderBook => Boolean = { ob =>
      ob.getBuyEntries.isEmpty && ob.getSellEntries.isEmpty
    }

    data.processingListener.foreach(_.processingDone(
      SolutionResult.builder()
        .orderBooks(data.orderBooks.filterNot(isEmpty).asJavaCollection)
        .transactions(data.transactions)
        .build()
    ))
  }

  private[this] def bookActorRef(data: Data, product: String): ActorRef = {
    data.books.getOrElseUpdate(product, context.actorOf(Props(classOf[OrderBookActor], self, product), product))
  }

  private[this] def gatherResults(data: Data): Unit = {
    data.books.values.foreach { _ ! OrderBookActor.GetTransactions }
  }
}

object ExchangeActor {

  private case class Data(processingListener: Option[ProcessingListener] = None,
                          activeBrokers: mutable.Set[String] = mutable.Set(),
                          books: mutable.Map[String, ActorRef] = mutable.Map(),
                          orderBooks: mutable.Set[OrderBook] = mutable.Set(),
                          transactions: util.HashSet[Transaction] = Sets.newHashSet[Transaction]()
                         )

  sealed trait ExchangeCommand

  // Idle state
  case class Register(processingListener: ProcessingListener)             extends ExchangeCommand
  case class Brokers(brokers: Set[String])                                extends ExchangeCommand
  case object Start                                                       extends ExchangeCommand

  // Active state
  case class ProcessModificationOrder(mo: ModificationOrder)              extends ExchangeCommand
  case class ProcessPositionOrder(po: PositionOrder)                      extends ExchangeCommand
  case class ProcessCancellationOrder(co: CancellationOrder)              extends ExchangeCommand
  case class BrokerStopped(broker: String)                                extends ExchangeCommand
  case class RecordTransactions(transactions: util.HashSet[Transaction])  extends ExchangeCommand
  case class RecordOrderBook(orderBook: OrderBook)                        extends ExchangeCommand
}