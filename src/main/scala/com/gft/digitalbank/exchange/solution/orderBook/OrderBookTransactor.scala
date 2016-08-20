package com.gft.digitalbank.exchange.solution.orderBook

import com.gft.digitalbank.exchange.model.Transaction
import com.gft.digitalbank.exchange.model.orders.PositionOrder
import com.gft.digitalbank.exchange.solution.OrderBookProduct

import scala.collection.mutable

private[orderBook] final class OrderBookTransactor(product: OrderBookProduct) {

  private[this] val transactions = mutable.ListBuffer.empty[Transaction]

  def getTransactions: Transactions = Transactions(transactions)

  def addTransaction(buy: PositionOrder, sell: PositionOrder, amountLimit: AmountLimit, priceLimit: PriceLimit): Boolean = {
    val t = buildTransaction(buy, sell, amountLimit, priceLimit)
    transactions prepend t
    true
  }

  private[this] def buildTransaction(buy: PositionOrder, sell: PositionOrder, amountLimit: AmountLimit, priceLimit: PriceLimit) = {
    Transaction
      .builder()
      .id(transactions.size + 1)
      .amount(amountLimit.amount)
      .price(priceLimit.price)
      .brokerBuy(buy.getBroker)
      .brokerSell(sell.getBroker)
      .clientBuy(buy.getClient)
      .clientSell(sell.getClient)
      .product(product.product)
      .build()
  }
}

case class Transactions(transactions: mutable.ListBuffer[Transaction]) extends AnyVal
