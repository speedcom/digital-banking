package com.gft.digitalbank.exchange.solution

import javax.jms.{Message, MessageConsumer, MessageListener, TextMessage}

import akka.actor.{Actor, ActorRef}
import spray.json._

import scala.util.Try

class ConsumerActor(messageConsumer: MessageConsumer, destination: String, exchangeActorRef: ActorRef) extends Actor {

  messageConsumer.setMessageListener(
      new MessageListener {
    override def onMessage(message: Message): Unit = message match {
      case txt: TextMessage =>
        val json = txt.getText.parseJson.asJsObject
        self.tell(json, ActorRef.noSender)
      case other =>
        throw new IllegalArgumentException(s"Received non-TextMessage from ActiveMQ: $other")
    }
  })

  override def receive: Receive = {
    case json: JsObject =>
      Try {
        val value = Unmarshall.jsonToJsonCommand(json)
        println(s"Decoded $json as $value")
        value match {
          case Left(ShutdownNotification(broker)) => exchangeActorRef ! BrokerStopped(broker.split("-").last)
          case Right(orderCommand) => exchangeActorRef ! ProcessMessage(orderCommand)
        }
      }.failed.foreach(_.printStackTrace())
  }
}
