package org.shelmet.heap.server

import org.shelmet.heap.model.{Root, Snapshot}
import org.shelmet.heap.util.SortUtil

class RootSetPage(snapshot : Snapshot) extends AbstractPage(snapshot) {
  override def run() {
    html("All Members of the Rootset") {

      val roots = snapshot.roots.sortWith(SortUtil.sortByFn(
        (l,r) => r.getType - l.getType,
        (l,r) => l.getDescription.compareTo(r.getDescription),
        (l,r) => l.getReferencedItem.map { _.toString }.getOrElse("").compareTo(
          r.getReferencedItem.map { _.toString }.getOrElse("")
        )
      ))

      roots.groupBy(_.getType) foreach {
        case (rootType, groupRoots) =>
          h2 {
            printEncoded(groupRoots.head.getTypeName + " References")
          }
          groupRoots foreach {
            root =>
              val x = root.getDescription
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