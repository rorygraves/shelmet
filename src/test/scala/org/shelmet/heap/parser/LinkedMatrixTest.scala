//package org.shelmet.heap.parser
//
//import com.typesafe.scalalogging.slf4j.Logging
//import scala.util.Random._
//import org.scalatest.FunSuite
//
//class LinkedMatrixTest extends FunSuite with Logging {
//
//  test("set and get") {
//    val matrix = new LinkedMatrix(1000)
//    (1 to 10000) foreach { i =>
//      val row = nextInt(1000)
//      val col = nextInt(1000)
//      val value = nextGaussian()
//      matrix.set(row, col, value)
//      assert(matrix.get(row, col) == value, (row, col))
//    }
//  }
//
//  test("parallel set with serial get") {
//    val matrix = new LinkedMatrix(100)
//    val indices = {
//      (1 to 10000) map { i =>
//        (nextInt(100), nextInt(100))
//      }
//    }.toSet
//    val expected = {
//      indices.par map { case (row, col) =>
//        val value = nextGaussian()
//        ((row, col) -> value) withEffect { _ =>
//          matrix.set(row, col, value)
//        }
//      }
//    }.seq
//    expected foreach { case ((row, col), value) =>
//      assert(matrix.get(row, col) == value, (row, col))
//    }
//  }
//}
//
//class CompressedColumnsTest extends FunSuite with Logging {
//
//  test("set and get") {
//    val matrix = new CompressedColumns(0)
//    (1 to 10000) foreach { i =>
//      val row = nextInt(1000)
//      val col = nextInt(1000)
//      matrix.set(row, col)
//      assert(matrix.get(row, col), (row, col))
//    }
//  }
//
//  test("parallel set with serial get") {
//    val matrix = new CompressedColumns(0)
//    val indices = {
//      (1 to 10000) map { i =>
//        (nextInt(100), nextInt(100))
//      }
//    }.toSet
//    indices.par foreach { case (row, col) =>
//      matrix.set(row, col)
//    }
//    indices foreach { case (row, col) =>
//      assert(matrix.get(row, col), (row, col))
//    }
//    for {
//      i <- 0 until 100
//      j <- 0 until 100
//      if !indices.contains(i, j)
//    } {
//      assert(!matrix.get(i, j), (i, j))
//    }
//  }
//}
