package com.gft.digitalbank.exchange.solution

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import com.gft.digitalbank.exchange.listener.ProcessingListener
import com.gft.digitalbank.exchange.model.SolutionResult
import spray.json.JsObject

class ExchangeActor extends Actor with ActorLogging {

  import context.dispatcher

  var processingListener: Option[ProcessingListener] = None
  var brokers: Set[String]                           = Set.empty
  val books = collection.mutable.Map.empty[String, ActorRef]

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
      case ProcessMessage(broker, json) =>
        val f = Unmarshall.jsonToJsonCommand(json)
        f.onSuccess {
          case Left(ShutdownNotification(broker)) => self ! BrokerStopped(broker)
          case Right(order)                       => handleOrderCommand(order)
        }
        f.onFailure {
          case t => t.printStackTrace()
        }
      case Start => println("Starting")
    }
  }

  def handleOrderCommand(orderCommand: OrderCommand): Unit = {
    orderCommand match {
      case b: Buy  => books.getOrElseUpdate(b.product, context.actorOf(Props(classOf[BookActor], b.product))) ! b
      case b: Sell => books.getOrElseUpdate(b.product, context.actorOf(Props(classOf[BookActor], b.product))) ! b
      case x       => ???
    }
  }

  def gatherResults(): Unit = {
    books.values.foreach {
      _ ! GetTransactions
    }

    processingListener.foreach { listener =>
      listener.processingDone(SolutionResult.builder().build())
    }
  }
}

sealed trait ExchangeCommand
case class Register(processingListener: ProcessingListener)   extends ExchangeCommand
case class Brokers(brokers: Set[String])                      extends ExchangeCommand
case class ProcessMessage(fromBroker: String, json: JsObject) extends ExchangeCommand

case class BrokerStopped(broker: String) extends ExchangeCommand

case object Start extends ExchangeCommand
