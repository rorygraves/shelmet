package org.shelmet.heap.exampleheap

object TestAppMain {
  var staticRef : TestAppMain = null
  def main(args : Array[String]) {

    staticRef = new TestAppMain(1)

    Thread.sleep(100000)
  }

}


class TestAppMain(intParam : Int) {
  val intField = intParam

  override def finalize() {}

}