package com.gft.digitalbank.exchange.solution

import java.util

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.gft.digitalbank.exchange.listener.ProcessingListener
import com.gft.digitalbank.exchange.model.orders.{CancellationOrder, ModificationOrder, PositionOrder, Side}
import com.gft.digitalbank.exchange.model.{OrderBook, Transaction}
import com.google.common.collect.Sets

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
    case ProcessPositionOrder(po) if po.getSide == Side.BUY  => handleBuyOrder(data, po)
    case ProcessPositionOrder(po) if po.getSide == Side.SELL => handleSellOrder(data, po)
    case ProcessModificationOrder(mo)                        => handleModificationOrder(data, mo)
    case ProcessCancellationOrder(co)                        => handleCancellationOrder(data, co)
    case BrokerStopped(broker)                               => handleBrokerStopped(data, broker)
    case RecordTransactions(ts)                              => handleRecordTransactions(data, ts)
    case RecordOrderBook(orderBook)                          => handleRecordOrderBook(data, orderBook)
  }

  @inline
  private[this] def handleBuyOrder(data: Data, po: PositionOrder): Unit = {
    orderBookActorRef(data, OrderBookProduct(po.getProduct)) ! OrderBookActor.BuyOrder(po)
  }

  @inline
  private[this] def handleSellOrder(data: Data, po: PositionOrder): Unit = {
    orderBookActorRef(data, OrderBookProduct(po.getProduct)) ! OrderBookActor.SellOrder(po)
  }

  @inline
  private[this] def handleModificationOrder(data: Data, mo: ModificationOrder): Unit = {
    data.orderBookActors.values.foreach(_ ! OrderBookActor.ModifyOrder(mo))
  }

  @inline
  private[this] def handleCancellationOrder(data: Data, co: CancellationOrder): Unit = {
    data.orderBookActors.values.foreach(_ ! OrderBookActor.CancelOrder(co))
  }

  @inline
  private[this] def handleBrokerStopped(data: Data, broker: Broker): Unit = {
    data.activeBrokers -= broker
    if (data.activeBrokers.isEmpty) gatherResults(data)
  }

  @inline
  private[this] def handleRecordTransactions(data: Data, ts: util.HashSet[Transaction]): Unit = {
    data.createdTransactions.addAll(ts)
  }

  @inline
  private[this] def handleRecordOrderBook(data: Data, orderBook: OrderBook): Unit = {
    data.createdOrderBooks += orderBook
    finishIfDone(data)
  }

  @inline
  private[this] def orderBookActorRef(data: Data, orderBookProduct: OrderBookProduct): ActorRef = {
    data.orderBookActors.getOrElseUpdate(
      key = orderBookProduct,
      op  = context.actorOf(OrderBookActor.props(self, orderBookProduct), name = orderBookProduct.product)
    )
  }

  @inline
  private[this] def gatherResults(data: Data): Unit = {
    data.orderBookActors.values.foreach(_ ! OrderBookActor.GetResults)
    finishIfDone(data)
  }

  @inline
  private[this] def finishIfDone(data: Data):Unit = {
    if (data.createdOrderBooks.size == data.orderBookActors.size) {
      context.system.terminate()
      sendSummaryToListener(data)
    }
  }

  @inline
  private[this] def sendSummaryToListener(data: Data) = {
    data.processingListener.foreach(_.processingDone(new SolutionResultBuilder()
      .build(data.createdOrderBooks, data.createdTransactions))
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
