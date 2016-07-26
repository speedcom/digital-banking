package com.gft.digitalbank.exchange.solution.orderBook

import java.util.{Comparator, PriorityQueue => JPriorityQueue}
import java.util.function.Predicate

import scala.collection.JavaConverters._

import com.gft.digitalbank.exchange.model.orders.PositionOrder

sealed abstract class PositionOrderCollection(comparator: Comparator[PositionOrder]) {

  private val orders = new JPriorityQueue[PositionOrder](comparator)

  def add(po: PositionOrder): Unit = orders.add(po)

  def removeIf(predicate: PositionOrder => Boolean): Boolean = {
    orders.removeIf(new Predicate[PositionOrder] {
      override def test(t: PositionOrder): Boolean = predicate(t)
    })
  }

  def peekOpt: Option[PositionOrder] = Option(orders.peek())

  def poll(): PositionOrder = orders.poll()

  def findBy(modifiedOrderId: Int, broker: String): Option[PositionOrder] = {
    orders.asScala.find(o => o.getId == modifiedOrderId && o.getBroker == broker)
  }

  def isEmpty: Boolean  = orders.isEmpty
  def nonEmpty: Boolean = !isEmpty
}

final class BuyOrders extends PositionOrderCollection(new Comparator[PositionOrder] {

  private val buyOrdering = Ordering.by[PositionOrder, (Int, Long)] { buy =>
    (buy.getDetails.getPrice * -1, buy.getTimestamp)
  }

  override def compare(o1: PositionOrder, o2: PositionOrder): Int = buyOrdering.compare(o1, o2)
})

final class SellOrders extends PositionOrderCollection(new Comparator[PositionOrder] {

  private val sellOrdering = Ordering.by[PositionOrder, (Int, Long)] { sell =>
    (sell.getDetails.getPrice, sell.getTimestamp)
  }

  override def compare(o1: PositionOrder, o2: PositionOrder): Int = sellOrdering.compare(o1, o2)
})