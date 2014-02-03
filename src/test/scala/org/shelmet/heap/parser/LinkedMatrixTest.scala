package org.shelmet.heap.parser

import com.typesafe.scalalogging.slf4j.Logging
import org.shelmet.heap.LinkedMatrix
import scala.util.Random._
import org.scalatest.FunSuite

class LinkedMatrixTest extends FunSuite with Logging {

  test("set and get") {
    val matrix = new LinkedMatrix(1000)
    (1 to 10000) foreach { i =>
      val row = nextInt(1000)
      val col = nextInt(1000)
      val value = nextGaussian()
      matrix.set(row, col, value)
      assert(matrix.get(row, col) == value, (row, col))
    }
  }

}
