package org.shelmet.heap.server

import org.shelmet.heap.model.Snapshot
import org.shelmet.heap.util.SortUtil

class RootSetPage(snapshot : Snapshot) extends AbstractPage(snapshot) {
  override def run() {
    html("All Members of the Rootset") {

      // TODO The rootType/root grouping and sorting should be shared between this class and ObjectRootsPage
      // group by returns an unsorted map so sort it
      snapshot.roots.groupBy(_.rootType).toList.sortBy(_._1.sortOrder) foreach {
        case (rootType, unsortedGroupRoots) =>
          h2 {
            printEncoded(rootType.name + " References")
          }

          val groupRoots = unsortedGroupRoots.sortWith(SortUtil.sortByFn(
            (l,r) => l.getDescription.compareTo(r.getDescription),
            (l,r) => l.getReferencedItem.map { _.toString }.getOrElse("").compareTo(
              r.getReferencedItem.map { _.toString }.getOrElse("")
            )
          ))

          groupRoots foreach {
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