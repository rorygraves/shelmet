package org.shelmet.heap.server

import org.eclipse.mat.snapshot.ISnapshot

class RootSetPage(snapshot : ISnapshot) extends AbstractPage(snapshot) {
  override def run() {

    // TODO Implement
    html("All Members of the Rootset") {
      h2 {
        printEncoded("Not currently supported")
      }
//      import scala.collection.JavaConversions._
//      val x = snapshot.getGCRoots.flatMap { rOid =>
//        val obj = snapshot.getObject(rOid)
//        snapshot.getObject(rOid).getGCRootInfo.map { ri =>
//          snapshot.getThreadStack()
//          (obj,ri)
//        }
//      }
    }
      // group by returns an unsorted map so sort it
//      oldSnapshot.roots.values.toList.groupBy(_.rootType).toList.sortBy(_._1.sortOrder) foreach {
//        case (rootType, unsortedGroupRoots) =>
//          h2 {
//            printEncoded(rootType.name + " References")
//          }
//
//          val groupRoots = unsortedGroupRoots.sortWith(SortUtil.sortByFn(
//            (l,r) => l.getDescription.compareTo(r.getDescription),
//            (l,r) => l.getReferencedItem.map(_.toString).getOrElse("").compareTo(
//              r.getReferencedItem.map(_.toString).getOrElse("")),
//            (l,r) => l.index - r.index
//          ))
//
//          groupRoots foreach {
//            root =>
//              printRoot(root)
//              root.referer.foreach { r =>
//                out.print("<small> (from ")
//                printThingAnchorTag(r.heapId, r.toString)
//                out.print(")</small>")
//              }
//              out.print(" :<br/>")
//              oldSnapshot.findHeapObject(root.valueHeapId) foreach { o =>
//                out.println("""<i class="icon-arrow-right"></i>""")
//                printThing(o)
//                out.println("<br/>")
//              }
//          }
//      }
//    }
  }
}