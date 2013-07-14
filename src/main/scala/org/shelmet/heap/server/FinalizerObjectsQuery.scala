package org.shelmet.heap.server

import org.shelmet.heap.model.Snapshot

class FinalizerObjectsQuery(snapshot : Snapshot) extends QueryHandler(snapshot) {

  override def run() {
    html("Objects pending finalization") {
      out.println("<a href='/finalizerSummary/'>Finalizer summary</a>")
      out.println("<h1>Objects pending finalization</h1>")
      snapshot.getFinalizerObjects.foreach { obj =>
        printThing(obj)
        out.println("<br/>")
      }
    }
  }
}