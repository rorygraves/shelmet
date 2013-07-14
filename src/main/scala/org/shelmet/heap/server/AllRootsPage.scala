package org.shelmet.heap.server

import org.shelmet.heap.model.Snapshot

class AllRootsPage(snapshot : Snapshot) extends QueryHandler(snapshot) {
  override def run() {
    html("All Members of the Rootset") {

      import org.shelmet.heap.util.SortUtil.sortByFirstThen

      val roots = snapshot.roots.sortWith( sortByFirstThen(
        (l,r) => r.getType - l.getType,
        (l,r) => l.getDescription.compareTo(r.getDescription)
      ))

      roots.groupBy(_.getType) foreach {
        case (rootType, roots) =>
          out.print("<h2>")
          printEncoded(roots.head.getTypeName + " References")
          out.println("</h2>")
          roots foreach {
            root =>
              printRoot(root)
              root.referer.foreach { r =>
                out.print("<small> (from ")
                printThingAnchorTag(r.heapId, r.toString)
                out.print(")</small>")
              }
              out.print(" :<br/>")
              snapshot.findHeapObject(root.valueHeapId) foreach { o =>
                out.println("""<i class="icon-arrow-right"></i>""")
                printThing(o)
                out.println("<br/>")
              }
          }
      }
    }
  }
}