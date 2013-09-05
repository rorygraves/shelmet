package org.shelmet.heap.server

import org.shelmet.heap.model._
import org.shelmet.heap.util.SortUtil
import org.shelmet.heap.HeapId
import scala.Some

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
            (l,r) => r.root.rootType.sortOrder - l.root.rootType.sortOrder,
            (l,r) => l.depth - r.depth,
            (l,r) => l.root.getDescription.compareTo(r.root.getDescription),
            (l,r) => l.root.getReferencedItem.map { _.toString }.getOrElse("").compareTo(
              r.root.getReferencedItem.map { _.toString }.getOrElse("")),
            (l,r) => l.root.index - r.root.index))

          out.print("<h1>References to ")
          printThing(target)
          out.println("</h1>")
          // TODO This is horrible - group and sort?
          var lastType: Option[RootType] = None
          for (reference <- refs) {
            val root: Root = reference.root
            if (Some(root.rootType) != lastType) {
              lastType = Some(root.rootType)
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
            var ref = reference.chain
            while (ref != Nil) {
              val next = ref.tail
              val obj: JavaHeapObject = ref.head
              printEncoded("--> ")
              printThing(obj)
              if (next != Nil) {
                printEncoded(" (" + obj.describeReferenceTo(next.head).mkString("/") + ")")
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