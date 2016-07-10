package com.gft.digitalbank.exchange.solution

object OrderCommandHandler {
  def handle(o: OrderCommand) = o match {
    case sell   : Sell        => ???
    case buy    : Buy         => ???
    case modify : Modify      => ???
    case cancel : Cancel      => ???
  }
}