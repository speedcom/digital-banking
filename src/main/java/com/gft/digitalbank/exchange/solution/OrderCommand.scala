package com.gft.digitalbank.exchange.solution

sealed trait OrderCommand {
  def messageType: MessageType
}

case class Bid(id: OrderId,
               timestamp: Timestamp,
               broker: Broker,
               client: Client,
               product: Product,
               details: Details) extends OrderCommand {
  override val messageType: MessageType = MessageType.Order
  val side: Side = Side.Buy
}

case class Sell(id: OrderId,
                timestamp: Timestamp,
                broker: Broker,
                client: Client,
                product: Product,
                details: Details) extends OrderCommand {
  override val messageType: MessageType = MessageType.Order
  val side: Side = Side.Sell
}

case class Modify(id: OrderId,
                  timestamp: Timestamp,
                  broker: Broker,
                  modifiedOrderId: ModifiedOrderId,
                  details: Details) extends OrderCommand {
  override val messageType: MessageType = MessageType.Modification
}

case class Cancel(id: OrderId,
                  timestamp: Timestamp,
                  broker: Broker,
                  cancelledOrderId: CancelledOrderId
                 ) extends OrderCommand {
  override val messageType: MessageType = MessageType.Cancellation
}

case object ShutdownNotification extends OrderCommand {
  override val messageType: MessageType = MessageType.Shutdown
}

sealed trait MessageType
object MessageType {
  case object Order extends MessageType
  case object Modification extends MessageType
  case object Cancellation extends MessageType
  case object Shutdown extends MessageType
}

sealed trait Side
object Side {
  case object Buy extends Side
  case object Sell extends Side
}

case class OrderId(id: Int) extends AnyVal
case class Timestamp(timestamp: Long) extends AnyVal
case class Broker(brokerId: String) extends AnyVal
case class Client(clientId: String) extends AnyVal
case class Product(ticker: String) extends AnyVal
case class Amount(amount: Int) extends AnyVal
case class Price(price: BigDecimal) extends AnyVal
case class Details(amount: Amount, price: Price)
case class CancelledOrderId(cancelledOrderId: String) extends AnyVal
case class ModifiedOrderId(modifiedOrderId: String) extends AnyVal