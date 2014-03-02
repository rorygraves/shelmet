package org.shelmet.heap.server

import org.shelmet.heap.model.Snapshot
import org.eclipse.mat.snapshot.ISnapshot

class FinalizerObjectsPage(oldSnapshot : Snapshot,snapshot : ISnapshot) extends AbstractPage(snapshot) {

  override def run() {
    html("Objects pending finalization") {
      out.println("<a href='/finalizerSummary/'>Finalizer summary</a>")
      out.println("<h1>Objects pending finalization</h1>")
      oldSnapshot.getFinalizerObjects.foreach { obj =>
        printThing(obj)
        out.println("<br/>")
      }
    }
  }
}