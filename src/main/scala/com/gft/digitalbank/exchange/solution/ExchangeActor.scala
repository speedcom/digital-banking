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
    case ProcessPositionOrder(po) if po.getSide == Side.BUY =>
      bookActorRef(data, po.getProduct) ! OrderBookActor.BuyOrder(po)
    case ProcessPositionOrder(po) if po.getSide == Side.SELL =>
      bookActorRef(data, po.getProduct) ! OrderBookActor.SellOrder(po)
    case ProcessModificationOrder(mo) =>
      data.orderBookActors.values.foreach (_ ! OrderBookActor.ModifyOrder(mo))
    case ProcessCancellationOrder(co) =>
      data.orderBookActors.values.foreach (_ ! OrderBookActor.CancelOrder(co))
    case BrokerStopped(broker) =>
      data.activeBrokers -= broker
      if (data.activeBrokers.isEmpty) gatherResults(data)
    case RecordTransactions(ts) =>
      data.createdTransactions.addAll(ts)
    case RecordOrderBook(orderBook) =>
      data.createdOrderBooks += orderBook
      if (data.createdOrderBooks.size == data.orderBookActors.size) {
        context.system.terminate()
        sendSummaryToListener(data)
      }
  }

  private[this] def sendSummaryToListener(data: Data) = {

    def prepareSolutionResult = {
      def isEmpty(ob: OrderBook): Boolean = ob.getBuyEntries.isEmpty && ob.getSellEntries.isEmpty
      SolutionResult.builder()
        .orderBooks(data.createdOrderBooks.filterNot(isEmpty).asJavaCollection)
        .transactions(data.createdTransactions)
        .build()
    }

    for {
      listener <- data.processingListener
      solution <- Option(prepareSolutionResult)
    } yield {
      listener.processingDone(solution)
    }
  }

  private[this] def bookActorRef(data: Data, product: String): ActorRef = {
    lazy val actor = context.actorOf(
      props = Props(classOf[OrderBookActor], self, product),
      name = product
    )
    data.orderBookActors.getOrElseUpdate(product, actor)
  }

  private[this] def gatherResults(data: Data): Unit = {
    data.orderBookActors.values.foreach { _ ! OrderBookActor.GetResults }
  }
}

object ExchangeActor {

  private case class Data(processingListener: Option[ProcessingListener] = None,
                          activeBrokers: mutable.Set[String] = mutable.Set(),
                          orderBookActors: mutable.Map[String, ActorRef] = mutable.Map(),
                          createdOrderBooks: mutable.Set[OrderBook] = mutable.Set(),
                          createdTransactions: util.HashSet[Transaction] = Sets.newHashSet[Transaction]())

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