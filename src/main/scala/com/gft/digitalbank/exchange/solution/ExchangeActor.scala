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

  private[this] def idle(data: Data): Receive = {
    case Register(listener)  => context.become(idle(data.copy(processingListener = Some(listener))))
    case Brokers(newBrokers) => context.become(idle(data.copy(activeBrokers = mutable.Set(newBrokers.toArray:_*))))
    case Start               => context.become(active(data))
  }

  private[this] def active(data: Data): Receive = {
    case ProcessPositionOrder(po) if po.getSide == Side.BUY =>
      bookActorRef(data, po.getProduct) ! OrderBookActor.BuyOrder(po)
    case ProcessPositionOrder(po) if po.getSide == Side.SELL =>
      bookActorRef(data, po.getProduct) ! OrderBookActor.SellOrder(po)
    case ProcessModificationOrder(mo) =>
      data.orderBookActors.values.foreach(_ ! OrderBookActor.ModifyOrder(mo))
    case ProcessCancellationOrder(co) =>
      data.orderBookActors.values.foreach(_ ! OrderBookActor.CancelOrder(co))
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

  private[this] def sendSummaryToListener(data: Data) = {
    for {
      listener <- data.processingListener
      solution <- Option(new SolutionResultBuilder().build(data.createdOrderBooks, data.createdTransactions))
    } yield {
      listener.processingDone(solution)
    }
  }
}

object ExchangeActor {

  private case class Data(processingListener: Option[ProcessingListener] = None,
                          activeBrokers: mutable.Set[Broker] = mutable.Set(),
                          orderBookActors: mutable.Map[String, ActorRef] = mutable.Map(),
                          createdOrderBooks: mutable.Set[OrderBook] = mutable.Set(),
                          createdTransactions: util.HashSet[Transaction] = Sets.newHashSet[Transaction]())

  sealed trait ExchangeCommand

  // Idle state
  case class Register(processingListener: ProcessingListener)             extends ExchangeCommand
  case class Brokers(brokers: Set[Broker])                                extends ExchangeCommand
  case object Start                                                       extends ExchangeCommand

  // Active state
  case class ProcessModificationOrder(mo: ModificationOrder)              extends ExchangeCommand
  case class ProcessPositionOrder(po: PositionOrder)                      extends ExchangeCommand
  case class ProcessCancellationOrder(co: CancellationOrder)              extends ExchangeCommand
  case class BrokerStopped(broker: Broker)                                extends ExchangeCommand
  case class RecordTransactions(transactions: util.HashSet[Transaction])  extends ExchangeCommand
  case class RecordOrderBook(orderBook: OrderBook)                        extends ExchangeCommand
}

class SolutionResultBuilder {

  private def isEmpty(ob: OrderBook): Boolean = {
    ob.getBuyEntries.isEmpty && ob.getSellEntries.isEmpty
  }

  def build(orderBooks: mutable.Set[OrderBook], transactions: util.HashSet[Transaction]): SolutionResult = {
    SolutionResult.builder()
      .orderBooks(orderBooks.filterNot(isEmpty).asJavaCollection)
      .transactions(transactions)
      .build()
  }
}