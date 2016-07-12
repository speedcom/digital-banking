package com.gft.digitalbank.exchange.solution

import javax.jms.{Message, MessageConsumer, MessageListener, TextMessage}
import akka.actor.{Actor, ActorRef}
import com.gft.digitalbank.exchange.model.orders.{CancellationOrder, ModificationOrder, PositionOrder, ShutdownNotification}
import scala.util.{Failure, Success}

class ConsumerActor(messageConsumer: MessageConsumer, destination: String, exchangeActorRef: ActorRef) extends Actor {

  messageConsumer.setMessageListener(new MessageListener {
    override def onMessage(message: Message): Unit = self.tell(message, ActorRef.noSender)
  })

  override def receive: Receive = {
    case txt: TextMessage =>
      Unmarshaller.unapply(txt) match {
        case Success(po: PositionOrder)        => ???
        case Success(co: CancellationOrder)    => ???
        case Success(mo: ModificationOrder)    => ???
        case Success(sn: ShutdownNotification) =>
          exchangeActorRef ! BrokerStopped(sn.getBroker.split("-").last)
          messageConsumer.close()
          context.stop(self)
        case Failure(msg)                      => msg.printStackTrace()
      }
    case other =>
      throw new IllegalArgumentException(s"Received non-TextMessage from ActiveMQ: $other")
  }
}
