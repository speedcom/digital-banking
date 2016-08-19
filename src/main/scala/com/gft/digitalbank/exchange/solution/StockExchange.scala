package com.gft.digitalbank.exchange.solution

import java.util
import javax.jms._
import javax.naming.InitialContext

import akka.actor.{ActorRef, ActorSystem, Props}
import com.gft.digitalbank.exchange.Exchange
import com.gft.digitalbank.exchange.listener.ProcessingListener

import scala.collection.JavaConverters._

class StockExchange extends Exchange {

  private[this] val context           = new InitialContext()
  private[this] val connectionFactory = context.lookup("ConnectionFactory").asInstanceOf[ConnectionFactory]
  private[this] val connection        = connectionFactory.createConnection()
  private[this] val session           = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)

  private[this] implicit val system  = ActorSystem()
  private[this] val exchangeActorRef = system.actorOf(Props[ExchangeActor], "exchange")

  override def register(processingListener: ProcessingListener): Unit = {
    exchangeActorRef.tell(ExchangeActor.Register(processingListener), ActorRef.noSender)
  }

  override def setDestinations(destinations: util.List[String]): Unit = {
    val uniqueDestinations = destinations.asScala.toSet
    val uniqueBrokers      = uniqueDestinations.map(Broker)

    exchangeActorRef.tell(ExchangeActor.Brokers(uniqueBrokers), ActorRef.noSender)

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
