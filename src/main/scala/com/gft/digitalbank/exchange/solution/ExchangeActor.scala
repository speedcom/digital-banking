package com.gft.digitalbank.exchange.solution

import java.util

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.gft.digitalbank.exchange.listener.ProcessingListener
import com.gft.digitalbank.exchange.model.orders.{CancellationOrder, ModificationOrder, PositionOrder, Side}
import com.gft.digitalbank.exchange.model.{OrderBook, SolutionResult, Transaction}
import com.google.common.collect.Sets

import scala.collection.JavaConverters._

class ExchangeActor extends Actor with ActorLogging {
  import ExchangeActor._

  var processingListener: Option[ProcessingListener] = None
  val activeBrokers = collection.mutable.Set.empty[String]
  val books         = collection.mutable.Map.empty[String, ActorRef]

  val orderBooks    = collection.mutable.Set.empty[OrderBook]
  val transactions  = Sets.newHashSet[Transaction]()

  override def receive: Receive = {
    case cmd: ExchangeCommand => handleExchangeCommand(cmd)
    case x                    => throw new IllegalArgumentException(s"Received wrong command: $x")
  }

  def handleExchangeCommand(exchangeCommand: ExchangeCommand): Unit = {
    println(s"Handling $exchangeCommand")
    exchangeCommand match {
      case Register(listener) =>
        println(s"Registering listener $listener")
        processingListener = Some(listener)
      case Brokers(newBrokers) =>
        activeBrokers ++= newBrokers
        println(s"Active brokers: ${ activeBrokers }")
      case BrokerStopped(broker) => {
          activeBrokers -= broker
          println(s"Active brokers: ${ activeBrokers }")
          if (activeBrokers.isEmpty) {
            gatherResults()
          }
        }
      case RecordTransactions(ts) =>
        transactions.addAll(ts)
      case RecordOrderBook(orderBook) =>
        orderBooks += orderBook

        if (orderBooks.size == books.size) {
          context.system.terminate()

          processingListener.foreach(_.processingDone(
            SolutionResult.builder()
              .orderBooks(orderBooks.filterNot(ob => ob.getBuyEntries.isEmpty && ob.getSellEntries.isEmpty).asJavaCollection)
              .transactions(transactions)
              .build()
          ))
        }
      case ProcessPositionOrder(po) =>
        activeBrokers += po.getBroker
        println(s"Active brokers: ${ activeBrokers }")
        val bookActor = bookActorRef(po.getProduct)
        if(po.getSide == Side.BUY)
          bookActor ! OrderBookActor.BuyOrder(po)
        else
          bookActor ! OrderBookActor.SellOrder(po)
      case ProcessModificationOrder(mo) =>
        activeBrokers += mo.getBroker
        println(s"Active brokers: ${ activeBrokers }")
        books.values.foreach (_ ! OrderBookActor.ModifyOrder(mo))
      case ProcessCancellationOrder(co) =>
        activeBrokers += co.getBroker
        println(s"Active brokers: ${ activeBrokers }")
        books.values.foreach (_ ! OrderBookActor.CancelOrder(co))
      case Start => println("Starting")
    }
  }

  private[this] def bookActorRef(product: String): ActorRef = {
    books.getOrElseUpdate(product, context.actorOf(Props(classOf[OrderBookActor], self, product), product))
  }

  private[this] def gatherResults(): Unit = {
    println("Will gather results as all brokers are gone")
    books.values.foreach {
      _ ! OrderBookActor.GetTransactions
    }
  }
}

object ExchangeActor {
  sealed trait ExchangeCommand
  case class Register(processingListener: ProcessingListener)             extends ExchangeCommand
  case class Brokers(brokers: Set[String])                                extends ExchangeCommand
  case class ProcessModificationOrder(mo: ModificationOrder)              extends ExchangeCommand
  case class ProcessPositionOrder(po: PositionOrder)                      extends ExchangeCommand
  case class ProcessCancellationOrder(co: CancellationOrder)              extends ExchangeCommand
  case class BrokerStopped(broker: String)                                extends ExchangeCommand
  case class RecordTransactions(transactions: util.HashSet[Transaction])  extends ExchangeCommand
  case class RecordOrderBook(orderBook: OrderBook)                        extends ExchangeCommand
  case object Start                                                       extends ExchangeCommand
}