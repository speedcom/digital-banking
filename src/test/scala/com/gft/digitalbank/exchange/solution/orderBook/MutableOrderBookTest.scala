package com.gft.digitalbank.exchange.solution.orderBook

import com.gft.digitalbank.exchange.solution.OrderBookProduct
import org.scalatest.{FlatSpec, Matchers}

class MutableOrderBookTest extends FlatSpec with Matchers {

  behavior of "Mutable Order Book"

  it should "produce empty transaction set and empty final order-book when nothing was processed" in {
    // given
    val googleOrderBook = new MutableOrderBook(OrderBookProduct("GOOG"))

    // when
    val finalTransactions = googleOrderBook.getTransactions
    val finalOrderBook    = googleOrderBook.getOrderBook

    // then
    finalTransactions.transactions.size() shouldBe 0
    finalOrderBook.getBuyEntries.size     shouldBe 0
    finalOrderBook.getSellEntries.size    shouldBe 0
  }

  it should "set correct product name for order-book" in {
    // given
    val googleOrderBook = new MutableOrderBook(OrderBookProduct("GOOG"))

    // when
    val finalOrderBook = googleOrderBook.getOrderBook

    // then
    finalOrderBook.getProduct shouldBe "GOOG"
  }
}
