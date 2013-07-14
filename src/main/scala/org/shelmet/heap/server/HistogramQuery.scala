package org.shelmet.heap.server

import org.shelmet.heap.model.{Snapshot, JavaClass}

/**
 * Prints histogram sortable by class name, count and size.
 */
class HistogramQuery(snapshot : Snapshot,query : String) extends QueryHandler(snapshot) {
  override def run() {
    val comparator: (JavaClass,JavaClass) => Boolean =
    query match {
      case "count" =>
      { case (first: JavaClass, second: JavaClass) =>
        (second.getInstancesCount(includeSubclasses = false) - first.getInstancesCount(includeSubclasses = false)) < 0
      }
      case "class" =>
      { case (first: JavaClass, second: JavaClass) =>
        first.name.compareTo(second.name) <0
      }
      case _ =>
      { case (first: JavaClass, second: JavaClass) =>
        (second.getTotalInstanceSize - first.getTotalInstanceSize) < 0
      }
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