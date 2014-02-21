package org.shelmet.heap.model.strings

import java.io.File
import org.shelmet.heap.parser.HprofReader
import org.shelmet.heap.model.{UnknownHeapObject, Snapshot}

object DeadRefs extends App {
  if(args.length != 1)
    throw new IllegalArgumentException("Usage: java org.shelmet.heap.model.strings.DeadRefs <dumpfile>")

  val startTime = System.currentTimeMillis()

  val dumpFile = new File(args(0))
  val reader = new HprofReader(dumpFile.getAbsolutePath)
  val model = Snapshot.readFromDump(reader,callStack = false,calculateRefs = true)

  Snapshot.setInstance(model)
  for(uho <- model.allObjects.filter(_.isInstanceOf[UnknownHeapObject]).take(2)) {
    val referRefs = uho.referers.map(r => r.toString + "." + r.describeReferenceTo(uho))
    println(s"id = ${uho.getIdString} - $referRefs - ")
  }
//  println(model.allObjects.filter(_.isInstanceOf[UnknownHeapObject]).size)

}
