package org.shelmet.heap.server

import org.shelmet.heap.model.{Snapshot, JavaHeapObject, ReferenceChain, Root}
import org.shelmet.heap.HeapId
import org.shelmet.heap.util.SortUtil

class ObjectRootsPage(snapshot : Snapshot,query : String,includeWeak: Boolean) extends AbstractPage(snapshot) {

  override def run() {
    val id = HeapId(parseHex(query))

    snapshot.findThing(id,createIfMissing = false) match {
      case Some(target) =>

        val title = if (includeWeak)
          "Rootset references to " + target + " (includes weak refs)"
        else
          "Rootset references to " + target + " (excludes weak refs)"

        html(title) {
          val refs = snapshot.rootsetReferencesTo(target, includeWeak).sortWith(SortUtil.sortByFn(
              (l,r) => r.obj.getRoot.getType - l.obj.getRoot.getType,
              (l,r) => l.depth - r.depth,
              (l,r) => l.obj.getRoot.getDescription.compareTo(r.obj.getRoot.getDescription),
              (l,r) => l.obj.getRoot.getReferencedItem.map { _.toString }.getOrElse("").compareTo(
                r.obj.getRoot.getReferencedItem.map { _.toString }.getOrElse(""))))

          out.print("<h1>References to ")
          printThing(target)
          out.println("</h1>")
          var lastType: Int = Root.INVALID_TYPE
          for (ref1 <- refs) {
            var ref: ReferenceChain = ref1
            val root: Root = ref.obj.getRoot
            if (root.getType != lastType) {
              lastType = root.getType
              h2(printEncoded(root.getTypeName + " References"))
            }
            out.print("<h3>")
            printRoot(root)
            root.referer.foreach { r =>
              out.print("<small> (from ")
              printThingAnchorTag(r.heapId,r.toString)
              out.print(")</small>")
            }
            out.print(" :</h3>")
            while (ref != null) {
              val next: ReferenceChain = ref.next
              val obj: JavaHeapObject = ref.obj
              printEncoded("--> ")
              printThing(obj)
              if (next != null) {
                printEncoded(" (" + obj.describeReferenceTo(next.obj).mkString("/") + ")")
              }
              out.println("<br/>")
              ref = next
            }
          }
          h2("Other queries")
          if (includeWeak) {
            printAnchor("objectRootsExcWeak/"+ hexString(id.id),"Exclude weak refs")
            out.println("<br/>")
          }
          if (!includeWeak) {
            printAnchor("objectRootsIncWeak/" + hexString(id.id),"Include weak refs")
            out.println("<br/>")
          }
        }
      case None =>
        html("Object not found for rootset") {
          out.println("object not found")
        }
    }
  }
}