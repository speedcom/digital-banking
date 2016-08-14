package com.gft.digitalbank.exchange.solution

import com.gft.digitalbank.exchange.model.{OrderBook, OrderEntry, Transaction}
import com.google.common.collect.Sets
import org.scalatest.{FlatSpec, Matchers}

import scala.collection.JavaConverters._
import scala.collection.mutable

class SolutionResultBuilderTest extends FlatSpec with Matchers {

  behavior of "Solution Result Builder"

  it should "NOT respect order-books with empty both buy and sell entries" in {
    // given
    val emptyEntries = OrderBook.builder()
      .product("GOOG")
      .buyEntries(List().asJavaCollection)
      .sellEntries(List().asJavaCollection)
      .build()

    val withEmptyOnlySells = OrderBook.builder()
      .product("GOOG")
      .buyEntries(List(buildOrder, buildOrder).asJavaCollection)
      .sellEntries(List().asJavaCollection)
      .build()

    val nonEmptyEntries = OrderBook.builder()
      .product("GOOG")
      .buyEntries(List(buildOrder).asJavaCollection)
      .sellEntries(List(buildOrder).asJavaCollection)
      .build()

    // when
    val result = new SolutionResultBuilder().build(
      orderBooks = mutable.Set(emptyEntries, withEmptyOnlySells, nonEmptyEntries),
      transactions = Sets.newHashSet[Transaction]()
    )

    // then
    result.getOrderBooks.size()                 shouldBe 2
    result.getOrderBooks.contains(emptyEntries) shouldBe false
  }

  private[this] def buildOrder = {
    OrderEntry.builder()
      .id(1)
      .amount(100)
      .price(99)
      .client("client-1")
      .broker("broker-1")
      .build()
  }
}
