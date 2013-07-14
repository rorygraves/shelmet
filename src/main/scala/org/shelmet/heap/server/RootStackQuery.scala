package org.shelmet.heap.server

import org.shelmet.heap.model.Snapshot

/**
 * Query to show the StackTrace for a given root
 */
class RootStackQuery(snapshot : Snapshot,query : String) extends QueryHandler(snapshot) {
  override def run() {
    val index = parseHex(query).asInstanceOf[Int]
    val root = snapshot.roots(index)
    if (root == null) {
      html("Root not found") {
        out.println("Root at " + index + " not found")
      }
    } else {
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