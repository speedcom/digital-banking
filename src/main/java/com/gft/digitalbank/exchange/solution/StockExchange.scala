package com.gft.digitalbank.exchange.solution

import java.util
import javax.jms._
import javax.naming.InitialContext

import com.gft.digitalbank.exchange.Exchange
import com.gft.digitalbank.exchange.listener.ProcessingListener

import scala.collection.JavaConverters._

class StockExchange extends Exchange {

  val context           = new InitialContext()
  val connectionFactory = context.lookup("ConnectionFactory").asInstanceOf[ConnectionFactory]

  val connection = connectionFactory.createConnection()
  connection.start()

  val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)

  var destinations: List[Destination]     = _
  var consumers   : List[MessageConsumer] = _

  var processingListener: ProcessingListener = _

  override def register(processingListener: ProcessingListener): Unit =  {
    println("registration")
    this.processingListener = processingListener
  }

  override def setDestinations(list: util.List[String]): Unit = {
    println("destinations")
    destinations = list.asScala.toList.map(session.createQueue)
    consumers    = destinations.map(session.createConsumer)
  }

  override def start(): Unit = {
    println("starting")

    consumers.foreach { c =>
      val msg = c.receive(200)
      msg match {
        case txt: TextMessage => println("txt: " + txt.getText)
        case _                => println("msg: " + msg)
      }
    }
  }
}
