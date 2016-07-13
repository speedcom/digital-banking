package com.gft.digitalbank.exchange.solution

import com.gft.digitalbank.exchange.model.orders.{CancellationOrder, ModificationOrder, PositionOrder, ShutdownNotification}

sealed trait OrderCommand

object OrderCommand {
  case class PositionOrderCommand(po: PositionOrder)         extends OrderCommand
  case class ModificationOrderCommand(mo: ModificationOrder) extends OrderCommand
  case class CancellationOrderCommand(co: CancellationOrder) extends OrderCommand
  case class ShutdownOrderCommand(so: ShutdownNotification)  extends OrderCommand
}
