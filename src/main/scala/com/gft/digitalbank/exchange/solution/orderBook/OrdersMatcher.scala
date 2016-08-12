package com.gft.digitalbank.exchange.solution.orderBook

import com.gft.digitalbank.exchange.model.orders.PositionOrder

case class PriceLimit(price: Int) extends AnyVal
case class AmountLimit(amount: Int) extends AnyVal
case class MatchedOrders(bestBuyOffer: PositionOrder, bestSellOffer: PositionOrder, priceLimit: PriceLimit, amountLimit: AmountLimit)

private[orderBook] class OrdersMatcher(buyOrders: BuyOrders, sellOrders: SellOrders) {

  def matchOrders(): Option[MatchedOrders] = {
    for {
      bestBuyOrder  <- buyOrders.peekOpt
      bestSellOrder <- sellOrders.peekOpt
      if bestBuyOrder.getDetails.getPrice >= bestSellOrder.getDetails.getPrice
      amountLimit = math.min(bestBuyOrder.getDetails.getAmount, bestSellOrder.getDetails.getAmount)
      priceLimit  = if(bestBuyOrder.getTimestamp < bestSellOrder.getTimestamp) bestBuyOrder.getDetails.getPrice else bestSellOrder.getDetails.getPrice
    } yield {
      MatchedOrders(bestBuyOrder, bestSellOrder, PriceLimit(priceLimit), AmountLimit(amountLimit))
    }
  }
}
