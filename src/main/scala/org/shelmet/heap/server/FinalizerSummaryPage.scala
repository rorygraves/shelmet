package org.shelmet.heap.server

import org.shelmet.heap.model.{Snapshot, JavaClass, JavaHeapObject}
import org.eclipse.mat.snapshot.ISnapshot

object FinalizerSummaryPage {

  private class HistogramElement(val clazz: JavaClass) extends Ordered[HistogramElement] {
    var count: Long = 0L

    def updateCount() {
      this.count += 1
    }

    override def compare(other: HistogramElement): Int = { (other.count - count).asInstanceOf[Int] }
  }
}

class FinalizerSummaryPage(snapshot : Snapshot,newSnapshot : ISnapshot) extends AbstractPage(newSnapshot) {
  import FinalizerSummaryPage.HistogramElement

  override def run() {
    html("Finalizer Summary") {
      out.println("<p>")
      out.println("<b><a href='/'>All Classes (excluding platform)</a></b>")
      out.println("</p>")
      val objects = snapshot.getFinalizerObjects
      printFinalizerSummary(objects.map(_.get))
    }
  }

  private def printFinalizerSummary(objects: List[JavaHeapObject]) {
    var count: Int = 0
    var map = Map[JavaClass, FinalizerSummaryPage.HistogramElement]()
    objects foreach {
      obj =>
      count += 1
      val clazz: JavaClass = obj.getClazz
      if (!map.contains(clazz))
        map += (clazz -> new HistogramElement(clazz))

      val element: HistogramElement = map.get(clazz).get
      element.updateCount()
    }

    out.println("<p>")
    out.println("<b>")
    out.println("Total ")
    if (count != 0)
      out.print("<a href='/finalizerObjects/'>instances</a>")
    else
      out.print("instances")

    out.println(" pending finalization: ")
    out.print(count)
    out.println("</b></p><hr/>")
    if (count > 0) {
      val elements = map.values.toList.sorted

      table {
        tableRow {
          out.println("<th>Count</th><th>Class</th>")
        }
        elements foreach { (element: HistogramElement) =>
          tableRow {
            tableData(out.println(element.count))
            tableData(printClass(element.clazz))
          }
        }
      }
    }
  }
}