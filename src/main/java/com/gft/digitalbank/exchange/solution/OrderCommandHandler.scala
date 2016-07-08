package com.gft.digitalbank.exchange.solution

object OrderCommandHandler {
  def handle(o: OrderCommand) = o match {
    case sell   : Sell        => ???
    case buy    : Bid         => ???
    case modify : Modify      => ???
    case cancel : Cancel      => ???
    case ShutdownNotification => ???
  }
}