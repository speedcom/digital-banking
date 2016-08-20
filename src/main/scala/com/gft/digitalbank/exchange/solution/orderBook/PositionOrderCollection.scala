package com.gft.digitalbank.exchange.solution.orderBook

import java.util.{Comparator, PriorityQueue => JPriorityQueue}
import java.util.function.Predicate

import scala.collection.JavaConverters._

import com.gft.digitalbank.exchange.model.orders.PositionOrder

private[orderBook] abstract class PositionOrderCollection(comparator: Comparator[PositionOrder]) {

  private[this] val orders = new JPriorityQueue[PositionOrder](comparator)

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

trait OrderComparator extends Comparator[PositionOrder] {

  object SpecializedIntComparator extends spire.algebra.Order[Int] {
    override def compare(x: Int, y: Int): Int = {
      if(x > y) 1
      else if(x == y) 0
      else -1
    }
  }

  object SpecializedLongComparator extends spire.algebra.Order[Long] {
    override def compare(x: Long, y: Long): Int = {
      if(x > y) 1
      else if(x == y) 0
      else -1
    }
  }
}

final class BuyOrders extends PositionOrderCollection(new OrderComparator {

  override def compare(o1: PositionOrder, o2: PositionOrder): Int = {
    SpecializedIntComparator.compare(o1.getDetails.getPrice, o2.getDetails.getPrice) match {
      case 0 => SpecializedLongComparator.compare(o1.getTimestamp, o2.getTimestamp)
      case o => -1 * o
    }
  }
})

final class SellOrders extends PositionOrderCollection(new OrderComparator {

  override def compare(o1: PositionOrder, o2: PositionOrder): Int = {
    SpecializedIntComparator.compare(o1.getDetails.getPrice, o2.getDetails.getPrice) match {
      case 0 => SpecializedLongComparator.compare(o1.getTimestamp, o2.getTimestamp)
      case o => o
    }
  }
})
