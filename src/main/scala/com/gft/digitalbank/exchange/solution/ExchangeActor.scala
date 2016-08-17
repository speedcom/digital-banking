package com.gft.digitalbank.exchange.solution

import java.util

import akka.actor.{Actor, ActorLogging, ActorRef}
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
      orderBookActorRef(data, po.getProduct) ! OrderBookActor.BuyOrder(po)
    case ProcessPositionOrder(po) if po.getSide == Side.SELL =>
      orderBookActorRef(data, po.getProduct) ! OrderBookActor.SellOrder(po)
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

  private[this] def orderBookActorRef(data: Data, product: String): ActorRef = {
    val orderBookProduct = OrderBookProduct(product)
    data.orderBookActors.getOrElseUpdate(
      key = orderBookProduct,
      op  = context.actorOf(OrderBookActor.props(self, orderBookProduct), name = product)
    )
  }

  private[this] def gatherResults(data: Data): Unit = {
    data.orderBookActors.values.foreach(_ ! OrderBookActor.GetResults)
  }

  private[this] def sendSummaryToListener(data: Data) = {
    data.processingListener.foreach(_.processingDone(
      new SolutionResultBuilder().build(data.createdOrderBooks, data.createdTransactions))
    )
  }
}

object ExchangeActor {

  private case class Data(processingListener: Option[ProcessingListener]           = None,
                          activeBrokers: mutable.Set[Broker]                       = mutable.Set.empty[Broker],
                          orderBookActors: mutable.Map[OrderBookProduct, ActorRef] = mutable.Map(),
                          createdOrderBooks: mutable.Set[OrderBook]                = mutable.Set.empty[OrderBook],
                          createdTransactions: util.HashSet[Transaction]           = Sets.newHashSet[Transaction]())

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
