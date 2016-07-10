package com.gft.digitalbank.exchange.solution

sealed trait OrderCommand

case object ShutdownNotification

case class Buy(id: Long,
               timestamp: Long,
               broker: String,
               client: String,
               product: String,
               details: Details) extends OrderCommand

case class Sell(id: Long,
                timestamp: Long,
                broker: String,
                client: String,
                product: String,
                details: Details) extends OrderCommand

case class Modify(id: Long,
                  timestamp: Long,
                  broker: String,
                  modifiedOrderId: String,
                  details: Details) extends OrderCommand

case class Cancel(id: Long,
                  timestamp: Long,
                  broker: String,
                  cancelledOrderId: String
                 ) extends OrderCommand

case class Details(amount: Int, price: Int)
