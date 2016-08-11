package com.gft.digitalbank.exchange.solution

import java.util
import javax.jms._
import javax.naming.InitialContext

import akka.actor.{ActorRef, ActorSystem, Props}
import com.gft.digitalbank.exchange.Exchange
import com.gft.digitalbank.exchange.listener.ProcessingListener

import scala.collection.JavaConverters._

class StockExchange extends Exchange {

  val context           = new InitialContext()
  val connectionFactory = context.lookup("ConnectionFactory").asInstanceOf[ConnectionFactory]
  val connection        = connectionFactory.createConnection()
  val session           = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)

  implicit val system  = ActorSystem()
  val exchangeActorRef = system.actorOf(Props[ExchangeActor], "exchange")

  override def register(processingListener: ProcessingListener): Unit = {
    exchangeActorRef.tell(ExchangeActor.Register(processingListener), ActorRef.noSender)
  }

  override def setDestinations(destinations: util.List[String]): Unit = {
    val uniqueDestinations = destinations.asScala.toSet

    exchangeActorRef.tell(ExchangeActor.Brokers(uniqueDestinations), ActorRef.noSender)

    uniqueDestinations.foreach { destination =>
      val queue           = session.createQueue(destination)
      val messageConsumer = session.createConsumer(queue)
      system.actorOf(Props(classOf[ConsumerActor], messageConsumer, destination, exchangeActorRef))
    }
  }

  override def start(): Unit = {
    exchangeActorRef.tell(ExchangeActor.Start, ActorRef.noSender)
    connection.start()
  }
}