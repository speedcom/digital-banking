package com.gft.digitalbank.exchange.solution

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import com.gft.digitalbank.exchange.listener.ProcessingListener
import com.gft.digitalbank.exchange.model.{ OrderBook, SolutionResult, Transaction }

import scala.collection.JavaConverters._

class ExchangeActor extends Actor with ActorLogging {

  var processingListener: Option[ProcessingListener] = None
  val activeBrokers = collection.mutable.Set.empty[String]
  val books         = collection.mutable.Map.empty[String, ActorRef]
  val transactions  = collection.mutable.Buffer.empty[Transaction]
  val orderBooks    = collection.mutable.Map.empty[String, OrderBook]

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
      case RecordTransaction(product, transaction) =>
        transactions.append(transaction)
        println(s"Transaction $product $transaction")
      case RecordOrderBook(product, orderBook) => {
          println(s"Recording $product $orderBook")
          orderBooks.update(product, orderBook)
          if (orderBooks.size == books.size) {
            val nonEmptyBooks = orderBooks.values.filterNot(o => o.getSellEntries.isEmpty && o.getBuyEntries.isEmpty).asJavaCollection
            context.system.terminate()
            processingListener.foreach(
                _.processingDone(
                    SolutionResult.builder().orderBooks(nonEmptyBooks).transactions(transactions.asJava).build()
                ))
          }
        }
//      case ProcessMessage(orderCommand) =>
//        activeBrokers += orderCommand.broker
//        println(s"Active brokers: ${ activeBrokers }")
//        handleOrderCommand(orderCommand)
      case Start => println("Starting")
    }
  }

  private[this] def bookActorRef(product: String): ActorRef = {
    books.getOrElseUpdate(product, context.actorOf(Props(classOf[BookActor], self, product), product))
  }
//
//  private[this] def handleOrderCommand(orderCommand: OrderCommand): Unit = orderCommand match {
//    case b: Buy  => bookActorRef(b.product) ! b
//    case s: Sell => bookActorRef(s.product) ! s
//    case x       => ???
//  }

  private[this] def gatherResults(): Unit = {
    println("Will gather results as all brokers are gone")
    books.values.foreach {
      _ ! GetTransactions
    }
  }
}

sealed trait ExchangeCommand
case class Register(processingListener: ProcessingListener)             extends ExchangeCommand
case class Brokers(brokers: Set[String])                                extends ExchangeCommand
//case class ProcessMessage(orderCommand: OrderCommand)                   extends ExchangeCommand
case class BrokerStopped(broker: String)                                extends ExchangeCommand
case class RecordTransaction(product: String, transaction: Transaction) extends ExchangeCommand
case class RecordOrderBook(product: String, orderBook: OrderBook)       extends ExchangeCommand

case object Start extends ExchangeCommand
