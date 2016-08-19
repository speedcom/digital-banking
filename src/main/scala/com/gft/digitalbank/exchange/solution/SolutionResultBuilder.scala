package com.gft.digitalbank.exchange.solution

import java.util
import com.gft.digitalbank.exchange.model.{OrderBook, SolutionResult, Transaction}
import scala.collection.mutable
import scala.collection.JavaConverters._

class SolutionResultBuilder {

  private def isEmpty(ob: OrderBook): Boolean = {
    ob.getBuyEntries.isEmpty && ob.getSellEntries.isEmpty
  }

  def build(orderBooks: mutable.Set[OrderBook], transactions: util.HashSet[Transaction]): SolutionResult = {
    SolutionResult.builder().orderBooks(orderBooks.filterNot(isEmpty).asJavaCollection).transactions(transactions).build()
  }
}
