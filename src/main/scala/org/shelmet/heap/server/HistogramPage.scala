package org.shelmet.heap.server

import org.shelmet.heap.model.{Snapshot, JavaClass}
import org.shelmet.heap.util.SortUtil

/**
 * Prints histogram sortable by class name, count and size.
 */
class HistogramPage(snapshot : Snapshot,sortParam : String) extends AbstractPage(snapshot) {
  override def run() {

    // the original code recomputed the values multiple times, instead capture and manipulate a tuple of
    // class, instance count, and  total size for sorting and display

    val comparator2: ((JavaClass,Int,Long),(JavaClass,Int,Long)) => Boolean =
      sortParam match {
        case "count" =>
          SortUtil.sortByFn(
            (l, r) => r._2 - l._2,
            (l, r) => l._1.name.compareTo(r._1.name))
        case "class" =>
          SortUtil.sortByFn((l, r) => l._1.name.compareTo(r._1.name))
        case _ => // total size
          SortUtil.sortByFn(
            (l, r) => (r._3 - l._3).toInt,
            (l, r) => l._1.name.compareTo(r._1.name))
      }

    val classItems = snapshot.getClasses.map { c =>
      (c,c.getInstancesCount(includeSubclasses = false),c.getTotalInstanceSize) }.toList.sortWith(comparator2)

    html("Heap Histogram") {
      table {
        out.println("""<tr><th><a href="/histogram/class">Class</a></th>""")
        out.println("""<th><a href="/histogram/count">Instance Count</a></th>""")
        out.println("""<th><a href="/histogram/size">Total Size</a></th></tr>""")
        for (clazz <- classItems) {
          tableRow {
            tableData(printClass(clazz._1))
            tableData(out.println(clazz._2))
            tableData(out.println(clazz._3))
          }
        }
      }
    }
  }
}