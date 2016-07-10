package com.gft.digitalbank.exchange.solution

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import com.gft.digitalbank.exchange.listener.ProcessingListener
import com.gft.digitalbank.exchange.model.{ OrderBook, SolutionResult, Transaction }

import scala.collection.JavaConverters._

class ExchangeActor extends Actor with ActorLogging {

  var processingListener: Option[ProcessingListener] = None
  var brokers: Set[String]                           = Set.empty
  val books        = collection.mutable.Map.empty[String, ActorRef]
  val transactions = collection.mutable.Buffer.empty[Transaction]
  val orderBooks   = collection.mutable.Map.empty[String, OrderBook]

  override def receive: Receive = {
    case cmd: ExchangeCommand => handleExchangeCommand(cmd)
    case x                    => throw new IllegalArgumentException(s"Received wrong command: $x")
  }

  def handleExchangeCommand(exchangeCommand: ExchangeCommand): Unit = {
    exchangeCommand match {
      case Register(listener)  => processingListener = Some(listener)
      case Brokers(newBrokers) => brokers = newBrokers
      case BrokerStopped(broker) => {
          println(broker)
          println(brokers)
          brokers -= broker
          if (brokers.isEmpty) {
            gatherResults()
          }
        }
      case RecordTransaction(product, transaction) => transactions.append(transaction)
      case RecordOrderBook(product, orderBook) => {
          orderBooks.update(product, orderBook)

          if (orderBooks.size == books.size) {
            val nonEmptyBooks = orderBooks.values.filterNot(o => o.getSellEntries.isEmpty && o.getBuyEntries.isEmpty).asJavaCollection
            processingListener.foreach(
                _.processingDone(
                    SolutionResult.builder().orderBooks(nonEmptyBooks).transactions(transactions.asJava).build()
                ))
          }
        }
      case ProcessMessage(broker, orderCommand) =>
        handleOrderCommand(orderCommand)
      case Start => println("Starting")
    }
  }

  private[this] def bookActorRef(product: String): ActorRef = {
    books.getOrElseUpdate(product, context.actorOf(Props(classOf[BookActor], self, product)))
  }

  private[this] def handleOrderCommand(orderCommand: OrderCommand): Unit = {
    orderCommand match {
      case b: Buy  => bookActorRef(b.product) ! b
      case s: Sell => bookActorRef(s.product) ! s
      case x       => ???
    }
  }

  private[this] def gatherResults(): Unit = {
    books.values.foreach {
      _ ! GetTransactions
    }
  }
}

sealed trait ExchangeCommand
case class Register(processingListener: ProcessingListener)               extends ExchangeCommand
case class Brokers(brokers: Set[String])                                  extends ExchangeCommand
case class ProcessMessage(fromBroker: String, orderCommand: OrderCommand) extends ExchangeCommand
case class BrokerStopped(broker: String)                                  extends ExchangeCommand
case class RecordTransaction(product: String, transaction: Transaction)   extends ExchangeCommand
case class RecordOrderBook(product: String, orderBook: OrderBook)         extends ExchangeCommand

case object Start extends ExchangeCommand
