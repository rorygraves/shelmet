package org.shelmet.heap.server

import org.shelmet.heap.model.{Snapshot, JavaHeapObject, ReachableObjects}
import org.shelmet.heap.HeapId

class ReachablePage(snapshot : Snapshot,query : String) extends AbstractPage(snapshot) {
  override def run() {
    html("Objects Reachable From " + query) {
      val id = HeapId(parseHex(query))

      val root: JavaHeapObject = id.getOpt(snapshot).get
      val ro: ReachableObjects = ReachableObjects.find(root)
      val totalSize: Long = ro.totalSize
      val things = ro.reachables
      val instances: Long = things.size

      out.print("<strong>")
      printThing(root)
      out.println("</strong><br/>")
      out.println("<br/>")
      for (thing <- things) {
        printThing(thing)
        out.println("<br/>")
      }
      h2(s"Total of $instances instances occupying $totalSize bytes.")
    }
  }
}