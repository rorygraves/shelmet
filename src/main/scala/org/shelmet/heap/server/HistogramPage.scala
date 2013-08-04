package org.shelmet.heap.server

import org.shelmet.heap.model.{Snapshot, JavaClass}
import org.shelmet.heap.util.SortUtil

/**
 * Prints histogram sortable by class name, count and size.
 */
class HistogramPage(snapshot : Snapshot,sortParam : String) extends AbstractPage(snapshot) {
  override def run() {
    val comparator: (JavaClass,JavaClass) => Boolean =
      sortParam match {
        case "count" =>
          SortUtil.sortByFn((l, r) => r.getInstancesCount(includeSubclasses = false) - l.getInstancesCount(includeSubclasses = false))
        case "class" =>
          SortUtil.sortByFn((l, r) => l.name.compareTo(r.name))
        case _ =>
          SortUtil.sortByFn(
            (l, r) => (r.getTotalInstanceSize - l.getTotalInstanceSize).toInt,
            (l, r) => l.name.compareTo(r.name))
      }

    val classes = snapshot.getClasses.toList.sortWith(comparator)
    html("Heap Histogram") {
      out.println("""<p>""")
      out.println("""<b><a href="/">All Classes (excluding platform)</a></b>""")
      out.println("</p>")
      table {
        out.println("""<tr><th><a href="/histo/class">Class</a></th>""")
        out.println("""<th><a href="/histo/count">Instance Count</a></th>""")
        out.println("""<th><a href="/histo/size">Total Size</a></th></tr>""")
        for (clazz <- classes) {
          tableRow {
            tableData(printClass(clazz))
            tableData(out.println(clazz.getInstancesCount(includeSubclasses = false)))
            tableData(out.println(clazz.getTotalInstanceSize))
          }
        }
      }
    }
  }
}