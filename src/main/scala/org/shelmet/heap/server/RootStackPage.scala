package org.shelmet.heap.server

import org.shelmet.heap.model.Snapshot
import org.eclipse.mat.snapshot.ISnapshot

/**
 * Query to show the StackTrace for a given root
 */
class RootStackPage(snapshot : Snapshot,newSnapshot : ISnapshot,query : String) extends AbstractPage(newSnapshot) {
  override def run() {
    val index = parseHex(query).asInstanceOf[Int]
    if(index < 0 || index >= snapshot.noRoots) {
      html("Root not found") {
        out.println("Root at " + index + " not found")
      }
    } else {
      val root = snapshot.roots(index)
      root.stackTrace match {
        case Some(t) if !t.frames.isEmpty =>
          html("Stack Trace for " + root.getDescription) {
            out.println("<p>")
            printStackTrace(t)
            out.println("</p>")
          }
        case _ =>
          html("Stack Trace not found") {
            out.println("No stack trace for " + root.getDescription)
          }
      }
    }
  }
}