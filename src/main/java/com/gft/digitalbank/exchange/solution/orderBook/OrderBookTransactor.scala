package com.gft.digitalbank.exchange.solution.orderBook

import java.util.{HashSet => JHashSet}

import com.gft.digitalbank.exchange.model.Transaction
import com.gft.digitalbank.exchange.model.orders.PositionOrder
import com.google.common.collect.Sets

final class OrderBookTransactor(product: String) {

  private val transactions = Sets.newHashSet[Transaction]()

  def getTransactions: Transactions = Transactions(transactions)

  def add(buy: PositionOrder, sell: PositionOrder, amountLimit: Int, priceLimit: Int): Boolean = {
    val t = buildTransaction(buy, sell, amountLimit, priceLimit)
    transactions.add(t)
  }

  private[this] def buildTransaction(buy: PositionOrder, sell: PositionOrder, amountLimit: Int, priceLimit: Int) = {
    Transaction.builder()
      .id(transactions.size() + 1)
      .amount(amountLimit)
      .price(priceLimit)
      .brokerBuy(buy.getBroker)
      .brokerSell(sell.getBroker)
      .clientBuy(buy.getClient)
      .clientSell(sell.getClient)
      .product(product)
      .build()
  }
}

case class Transactions(transactions: JHashSet[Transaction]) extends AnyVal