package com.gft.digitalbank.exchange.solution

import javax.jms.{ Message, MessageConsumer, MessageListener, TextMessage }

import akka.actor.{ Actor, ActorRef }
import spray.json._

class BrokerActor(messageConsumer: MessageConsumer, destination: String, exchangeActorRef: ActorRef) extends Actor {

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
      Unmarshall.jsonToJsonCommand(json) match {
        case Left(_)             => exchangeActorRef ! BrokerStopped(destination)
        case Right(orderCommand) => exchangeActorRef ! ProcessMessage(destination, orderCommand)
      }
  }
}
