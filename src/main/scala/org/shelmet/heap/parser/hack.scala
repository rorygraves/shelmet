//package org.shelmet.heap.parser
//
//import java.util.concurrent.atomic.{AtomicReferenceArray, AtomicLong}
//import com.typesafe.scalalogging.slf4j.Logging
//import java.util.concurrent.locks.ReentrantLock
//import debox.{Set => DSet}
//
//object `package` {
//
//  implicit class PimpedEffects[T <: AnyRef](val a: T) {
//    def withEffect(effect: T => Unit): T = {
//      effect(a)
//      a
//    }
//
//    def synced[R](f: T => R): R = a synchronized {
//      f(a)
//    }
//  }
//
//}
//
//// mutable, but allows concurrent set
//class LinkedMatrix(size: Int) {
//
//  class Node(var row: Int,
//             var col: Int,
//             var value: Double,
//             var rowTail: Node,
//             var colTail: Node)
//
//  // rows and cols store the zeroeth col and row respectively
//  // (0, 0) is arbitrarily stored in "rows"
//  private val cols = Array.ofDim[Node](size)
//  private val rows = Array.ofDim[Node](size)
//
//  rows(size - 1) = new Node(size - 1, 0, 0, null, null)
//  cols(size - 1) = new Node(0, size - 1, 0, null, null)
//  (0 until size - 1).reverse foreach {
//    i =>
//      rows(i) = new Node(i, 0, 0, rows(i + 1), rows(i + 1))
//      cols(i) = new Node(0, i, 0, cols(i + 1), cols(i + 1))
//  }
//  rows(0).colTail = cols(1)
//  cols(size - 1).colTail = rows(1)
//
//  def get(row: Int, col: Int): Double = {
//    require(row >= 0 && col >= 0)
//    require(row < size && col < size)
//
//    if (col == 0) rows(row).value
//    else if (row == 0) cols(col).value
//    else {
//      val prev = findPreviousOnRow(row, col)
//      if (prev.col == col) prev.value
//      else prev.rowTail match {
//        case null => 0
//        case node => node.value
//      }
//    }
//  }
//
//  def set(row: Int, col: Int, value: Double) {
//    require(row >= 0 && col >= 0)
//    require(row < size && col < size)
//
//    rows(row) synchronized {
//      cols(col) synchronized {
//        if (col == 0) rows(row).value = value
//        else if (row == 0) cols(col).value = value
//        else findWithColNeighbours(row, col) match {
//          /*
//          We should *always* have a left (up) neighbour on the same row (col)
//          (because zeroth rows/columns are treated separately)
//          but the right (down) neighbour might be null or on a different row (col). The left (up)
//          neighbour may have its rowTail (colTail) modified, but we must not mutate the right (down).
//
//          We are synchronized on the current row and column, but the previous node(s) still have
//          free dimensions (e.g. lock on (10,20) but previous row node might be (10, 15) which
//          can also be accessed by lock on (30,15)). This is OK because we're only updating
//          the row tail (in the case of a Node with a free column dimension) or the column tail
//          (in the case of a Node with a free row dimension).
//          */
//          case (up, Some(existing), down) if value == 0 => remove(row, col, up, down)
//          case (up, Some(existing), down) => existing.value = value
//          case (up, None, down) if value == 0 =>
//          case (up, None, down) => insert(row, col, up, down, value)
//        }
//      }
//    }
//  }
//
//  private def remove(row: Int, col: Int, up: Node, down: Node) {
//    findWithRowNeighbours(row, col) match {
//      case (left, Some(_), right) =>
//        up.colTail = down
//        left.rowTail = right
//      case _ => throw new IllegalStateException
//    }
//  }
//
//  private def insert(row: Int, col: Int, up: Node, down: Node, value: Double) {
//    findWithRowNeighbours(row, col) match {
//      case (left, None, right) =>
//        val insert = new Node(row, col, value, right, down)
//        up.colTail = insert
//        left.rowTail = insert
//
//      case _ => throw new IllegalStateException
//    }
//  }
//
//  private def findWithRowNeighbours(row: Int, col: Int): (Node, Option[Node], Node) = {
//    val prev = findPreviousOnRow(row, col)
//    assert(prev.row == row, (row, col) + " but got " + prev.col)
//    if (prev.rowTail == null) (prev, None, null)
//    else if (prev.colTail != null && prev.colTail.row == row && prev.rowTail.col == col) (prev, Some(prev.rowTail), prev.rowTail.rowTail)
//    else (prev, None, prev.rowTail)
//  }
//
//  private def findWithColNeighbours(row: Int, col: Int): (Node, Option[Node], Node) = {
//    val prev = findPreviousOnCol(row, col)
//    assert(prev.col == col, (row, col) + " but got " + prev.col)
//    if (prev.colTail == null) (prev, None, null)
//    else if (prev.rowTail != null && prev.rowTail.col == col && prev.colTail.row == row) (prev, Some(prev.colTail), prev.colTail.colTail)
//    else (prev, None, prev.colTail)
//  }
//
//  private def findPreviousOnRow(row: Int, col: Int): Node = {
//    assert(row > 0 && col > 0)
//    var latest = rows(row)
//    assert(latest.row == row)
//    while (latest.rowTail != null && latest.rowTail.row == row && latest.rowTail.col < col) {
//      latest = latest.rowTail
//    }
//    latest
//  }
//
//  private def findPreviousOnCol(row: Int, col: Int): Node = {
//    assert(row > 0 && col > 0)
//    var latest = cols(col)
//    assert(latest.col == col)
//    while (latest.colTail != null && latest.colTail.col == col && latest.colTail.row < row) {
//      latest = latest.colTail
//    }
//    latest
//  }
//
//}
//
//
//// this is a boolean graph, not a matrix
//class CompressedColumns(val columnSize: Int) extends Logging {
//  self =>
//
//  // gives 139mb of LLists (plus atomic ref array) with 17mb int[] on 2,492,432 refs with 5,819,345 links (201MB heap dump)
//  sealed trait IList {
//    def head: Int
//
//    def tail: IList = ???
//
//    def ::(e: Int): IList = new ILink(e, this)
//
//    def isEmpty: Boolean
//
//    def contains(e: Int): Boolean = false
//
//    def toSet: DSet[Int] = {
//      DSet.empty[Int] withEffect { set =>
//        var probe = this
//        while (!probe.isEmpty) {
//          set add probe.head
//          probe = probe.tail
//        }
//      }
//    }
//  }
//
//  object INil extends IList {
//    def head = ???
//
//    def isEmpty = true
//    override def toString = "INil"
//  }
//
//  class ILink(override val head: Int,
//              override val tail: IList) extends IList {
//    def isEmpty = false
//    override def toString = head + "::" + tail
//  }
//
//  private val enters = new AtomicLong
//
//  // don't call until we finish creating!
//  lazy val count: Long = (0L /: cols){_ + _.toSet.size.toLong}
//
//  def entered = enters.get
//
////  private val cols = new AtomicReferenceArray[IList](columnSize)
//  private val cols = Array.fill[IList](columnSize)(INil)
//
//  def get(col: Int) = cols(col).toSet
//
//  def get(row: Int, col: Int): Boolean = get(col)(row)
//
////  private val locks = Array.fill(Runtime.getRuntime.availableProcessors()) {
////    new ReentrantLock()
////  }
////  private def getLock(col: Int) = locks(col % locks.length)
//
//  def set(row: Int, col: Int) {
//    // (0,0) is null
//    require(row > 0)
//    require(col > 0 && col < columnSize, col + ">= " + columnSize)
//
//    // we intentionally allow dupes for insertion speed (e.g. class / array can ref same thing twice)
////    logger.info(row + " " + col)
//    cols.update(col, row :: cols(col))
////    logger.info(row + " appended to " + col + " = " + cols(col))
//
////    if (!cols.compareAndSet(col, null, row :: INil)) {
//      //    if (!cols.compareAndSet(col, null, row :: INil)) {
////      val lock = getLock(col)
////      lock.lock()
////      try {
////        val existing = cols.get(col)
////        cols.set(col, row :: existing)
////      } finally lock.unlock()
////    }
//    enters.getAndIncrement()
//  }
//}