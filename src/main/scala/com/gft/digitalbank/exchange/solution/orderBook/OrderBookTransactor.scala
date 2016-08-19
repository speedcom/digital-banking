package com.gft.digitalbank.exchange.solution.orderBook

import java.util.{HashSet => JHashSet}

import com.gft.digitalbank.exchange.model.Transaction
import com.gft.digitalbank.exchange.model.orders.PositionOrder
import com.gft.digitalbank.exchange.solution.OrderBookProduct
import com.google.common.collect.Sets

private[orderBook] final class OrderBookTransactor(product: OrderBookProduct) {

  private[this] val transactions = Sets.newHashSet[Transaction]()

  def getTransactions: Transactions = Transactions(transactions)

  def addTransaction(buy: PositionOrder, sell: PositionOrder, amountLimit: AmountLimit, priceLimit: PriceLimit): Boolean = {
    val t = buildTransaction(buy, sell, amountLimit, priceLimit)
    transactions.add(t)
  }

  private[this] def buildTransaction(buy: PositionOrder, sell: PositionOrder, amountLimit: AmountLimit, priceLimit: PriceLimit) = {
    Transaction
      .builder()
      .id(transactions.size() + 1)
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

case class Transactions(transactions: JHashSet[Transaction]) extends AnyVal
