package com.gft.digitalbank.exchange.solution

import java.util
import javax.jms._
import javax.naming.InitialContext

import akka.actor.{ ActorRef, ActorSystem, Props }
import com.gft.digitalbank.exchange.Exchange
import com.gft.digitalbank.exchange.listener.ProcessingListener
import spray.json._

import scala.collection.JavaConverters._

class StockExchange extends Exchange {

  val context           = new InitialContext()
  val connectionFactory = context.lookup("ConnectionFactory").asInstanceOf[ConnectionFactory]
  val connection        = connectionFactory.createConnection()
  val session           = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)

  implicit val system  = ActorSystem()
  val exchangeActorRef = system.actorOf(Props[ExchangeActor], "exchange")

  override def register(processingListener: ProcessingListener): Unit = {
    exchangeActorRef.tell(Register(processingListener), ActorRef.noSender)
  }

  override def setDestinations(destinations: util.List[String]): Unit = {
    val uniqueDestinations = destinations.asScala.toSet
    exchangeActorRef.tell(Brokers(uniqueDestinations), ActorRef.noSender)
    uniqueDestinations.foreach { destination =>
      val queue           = session.createQueue(destination)
      val messageConsumer = session.createConsumer(queue)
      messageConsumer.setMessageListener(
          new MessageListener {
        override def onMessage(message: Message): Unit = message match {
          case txt: TextMessage =>
            val json = txt.getText.parseJson.asJsObject
            exchangeActorRef.tell(ProcessMessage(destination, json), ActorRef.noSender)
          case other =>
            throw new IllegalArgumentException(s"Received non-TextMessage from ActiveMQ: $other")
        }
      })
    }
  }

  override def start(): Unit = {
    exchangeActorRef.tell(Start, ActorRef.noSender)
    connection.start()
  }
}
