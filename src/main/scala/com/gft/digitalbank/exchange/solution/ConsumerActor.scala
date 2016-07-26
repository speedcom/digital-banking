package com.gft.digitalbank.exchange.solution

import javax.jms.{Message, MessageConsumer, MessageListener, TextMessage}

import akka.actor.{Actor, ActorRef}
import com.gft.digitalbank.exchange.solution.OrderCommand._

import scala.util.{Failure, Success}

class ConsumerActor(messageConsumer: MessageConsumer, destination: String, exchangeActorRef: ActorRef) extends Actor {

  messageConsumer.setMessageListener(new MessageListener {
    override def onMessage(message: Message): Unit = self.tell(message, ActorRef.noSender)
  })

  override def receive: Receive = {
    case txt: TextMessage =>
      Unmarshaller(txt) match {
        case Success(order: PositionOrderCommand)     => exchangeActorRef ! ExchangeActor.ProcessPositionOrder(order.po)
        case Success(order: CancellationOrderCommand) => exchangeActorRef ! ExchangeActor.ProcessCancellationOrder(order.co)
        case Success(order: ModificationOrderCommand) => exchangeActorRef ! ExchangeActor.ProcessModificationOrder(order.mo)
        case Success(order: ShutdownOrderCommand)     =>
          exchangeActorRef ! ExchangeActor.BrokerStopped(order.so.getBroker.split("-").last)
          messageConsumer.close()
          context.stop(self)
        case Failure(msg) => msg.printStackTrace()
      }
    case other =>
      throw new IllegalArgumentException(s"Received non-TextMessage from ActiveMQ: $other")
  }
}