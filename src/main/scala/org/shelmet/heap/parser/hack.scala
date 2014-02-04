package org.shelmet.heap.parser

import java.util.concurrent.locks.ReentrantLock
import scala.collection.immutable.ListSet
import scala.collection.parallel.mutable
import scala.collection


//import no.uib.cipr.matrix.sparse.LinkedSparseMatrix
//
//// https://github.com/fommil/matrix-toolkits-java/issues/43
//object LinkedMatrix {
//  def apply(size: Int): LinkedSparseMatrix = {
//    val matrix = new LinkedSparseMatrix(size, size)
//    // this should significantly speed up future lookups
//    (0 until size) foreach { i =>
//      matrix.set(i, i, 1.0)
//    }
//    matrix
//  }
//}
//

object `package` {

  class PimpedEffects[T](val a: T) extends AnyVal {
    def withEffect(effect: T => Unit): T = {
      effect(a)
      a
    }
  }

  implicit def pimpedEffect[T](a: T) = new PimpedEffects(a)
}

// mutable, but allows concurrent set
class LinkedMatrix(size: Int) {

  class Node(var row: Int,
             var col: Int,
             var value: Double,
             var rowTail: Node,
             var colTail: Node)

  // rows and cols store the zeroeth col and row respectively
  // (0, 0) is arbitrarily stored in "rows"
  private val cols = Array.ofDim[Node](size)
  private val rows = Array.ofDim[Node](size)

  rows(size - 1) = new Node(size - 1, 0, 0, null, null)
  cols(size - 1) = new Node(0, size - 1, 0, null, null)
  (0 until size - 1).reverse foreach {
    i =>
      rows(i) = new Node(i, 0, 0, rows(i + 1), rows(i + 1))
      cols(i) = new Node(0, i, 0, cols(i + 1), cols(i + 1))
  }
  rows(0).colTail = cols(1)
  cols(size - 1).colTail = rows(1)

  def get(row: Int, col: Int): Double = {
    require(row >= 0 && col >= 0)
    require(row < size && col < size)

    if (col == 0) rows(row).value
    else if (row == 0) cols(col).value
    else {
      val prev = findPreviousOnRow(row, col)
      if (prev.col == col) prev.value
      else prev.rowTail match {
        case null => 0
        case node => node.value
      }
    }
  }

  // to avoid synchronized
  // TODO: use synchronized or reduce lock array size
  private val colLocks = Array.fill(size){new ReentrantLock()}
  private val rowLocks = Array.fill(size){new ReentrantLock()}

  def set(row: Int, col: Int, value: Double) {
    require(row >= 0 && col >= 0)
    require(row < size && col < size)

    rowLocks(row).lock()
    try {
      colLocks(col).lock()
      try {
        if (col == 0) rows(row).value = value
        else if (row == 0) cols(col).value = value
        else findWithColNeighbours(row, col) match {
          /*
          We should *always* have a left (up) neighbour on the same row (col)
          (because zeroth rows/columns are treated separately)
          but the right (down) neighbour might be null or on a different row (col). The left (up)
          neighbour may have its rowTail (colTail) modified, but we must not mutate the right (down).

          We are synchronized on the current row and column, but the previous node(s) still have
          free dimensions (e.g. lock on (10,20) but previous row node might be (10, 15) which
          can also be accessed by lock on (30,15)). This is OK because we're only updating
          the row tail (in the case of a Node with a free column dimension) or the column tail
          (in the case of a Node with a free row dimension).
          */
          case (up, Some(existing), down) if value == 0 => remove(row, col, up, down)
          case (up, Some(existing), down) => existing.value = value
          case (up, None, down) if value == 0 =>
          case (up, None, down) => insert(row, col, up, down, value)
        }
      } finally colLocks(col).unlock()
    } finally rowLocks(row).unlock()
  }

  private def remove(row: Int, col: Int, up: Node, down: Node) {
    findWithRowNeighbours(row, col) match {
      case (left, Some(_), right) =>
        up.colTail = down
        left.rowTail = right
      case _ => throw new IllegalStateException
    }
  }

  private def insert(row: Int, col: Int, up: Node, down: Node, value: Double) {
    findWithRowNeighbours(row, col) match {
      case (left, None, right) =>
        val insert = new Node(row, col, value, right, down)
        up.colTail = insert
        left.rowTail = insert

      case _ => throw new IllegalStateException
    }
  }

  private def findWithRowNeighbours(row: Int, col: Int): (Node, Option[Node], Node) = {
    val prev = findPreviousOnRow(row, col)
    assert(prev.row == row, (row, col) + " but got " + prev.col)
    if (prev.rowTail == null) (prev, None, null)
    else if (prev.colTail != null && prev.colTail.row == row && prev.rowTail.col == col) (prev, Some(prev.rowTail), prev.rowTail.rowTail)
    else (prev, None, prev.rowTail)
  }

  private def findWithColNeighbours(row: Int, col: Int): (Node, Option[Node], Node) = {
    val prev = findPreviousOnCol(row, col)
    assert(prev.col == col, (row, col) + " but got " + prev.col)
    if (prev.colTail == null) (prev, None, null)
    else if (prev.rowTail != null && prev.rowTail.col == col && prev.colTail.row == row) (prev, Some(prev.colTail), prev.colTail.colTail)
    else (prev, None, prev.colTail)
  }

  private def findPreviousOnRow(row: Int, col: Int): Node = {
    assert(row > 0 && col > 0)
    var latest = rows(row)
    assert(latest.row == row)
    while (latest.rowTail != null && latest.rowTail.row == row && latest.rowTail.col < col) {
      latest = latest.rowTail
    }
    latest
  }

  private def findPreviousOnCol(row: Int, col: Int): Node = {
    assert(row > 0 && col > 0)
    var latest = cols(col)
    assert(latest.col == col)
    while (latest.colTail != null && latest.colTail.col == col && latest.colTail.row < row) {
      latest = latest.colTail
    }
    latest
  }

}

// this is a boolean graph, not a matrix
class CompressedColumns(size: Int) {

//  private val cols = Array.fill(size) {scala.collection.mutable.BitSet.empty}
  private val cols = Array.fill(size) {scala.collection.mutable.TreeSet.empty[Int]}
  //private val cols = Array.fill(size) {Set[Int]()}

  def get(row: Int, col: Int) = {
    require(row >= 0 && col >= 0)
    require(row < size && col < size)
    cols(col)(row)
  }

  def set(row: Int, col: Int) {
    require(row >= 0 && col >= 0)
    require(row < size && col < size)
    cols(col) synchronized {
      cols(col) = cols(col) + row
    }
  }

}