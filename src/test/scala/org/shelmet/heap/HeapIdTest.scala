package org.shelmet.heap

import org.scalatest.FunSuite

class HeapIdTest extends FunSuite {

  test("A HeapId should report only 0 as null") {
    assert(HeapId(0).isNull)
    assert(HeapId(1).isNull === false)
  }

  test("A HeapId should format itself as a hex value") {
    assert(HeapId(0).toHex === "0x0")
  }

}
