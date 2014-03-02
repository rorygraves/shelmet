package org.shelmet.heap.server

import org.shelmet.heap.util.SortUtil
import org.eclipse.mat.snapshot.ISnapshot
import org.eclipse.mat.snapshot.model.IClass

object HistogramPage {
  case class Entry(clazz : IClass,instCount : Int,totalSize : Long,totalRetained : Long) {
  }
}

/**
 * Prints histogram sortable by class name, count and size.
 */
class HistogramPage(snapshot : ISnapshot,sortParam : String) extends AbstractPage(snapshot) {

  import HistogramPage._
  override def run() {

    // the original code recomputed the values multiple times, instead capture and manipulate a tuple of
    // class, instance count, and  total size for sorting and display

    val comparator2: (Entry,Entry) => Boolean =
      sortParam match {
        case "count" =>
          SortUtil.sortByFn(
            (l, r) => r.instCount - l.instCount,
            (l, r) => l.clazz.getName.compareTo(r.clazz.getName))
        case "class" =>
          SortUtil.sortByFn((l, r) => l.clazz.getName.compareTo(r.clazz.getName))
        case _ => // total size
          SortUtil.sortByFn(
            (l, r) => (r.totalSize - l.totalSize).toInt,
            (l, r) => l.clazz.getName.compareTo(r.clazz.getName))
      }

    import scala.collection.JavaConversions._
    val classItems = snapshot.getClasses.map { c =>
      val (totalInstanceSize,totalRetained) = if(c.isArrayType) {
        (snapshot.getHeapSize(c.getObjectIds),
        c.getObjectIds.map(snapshot.getRetainedHeapSize).sum)
      } else {
        (c.getHeapSizePerInstance * c.getNumberOfObjects,
        c.getObjectIds.map(snapshot.getRetainedHeapSize).sum)
      }
      Entry(c,c.getNumberOfObjects,totalInstanceSize,totalRetained) }.toList.sortWith(comparator2)

    html("Heap Histogram") {
      table {
        tableRow {
          out.println("""<th><a href="/histogram/class">Class</a></th>""")
          out.println("""<th><a href="/histogram/count">Instance Count</a></th>""")
          out.println("""<th><a href="/histogram/size">Total Size</a></th>""")
          out.println("""<th>Size retained by instances</th>""")
          out.println("""<th>Avg instance retained size</th>""")
        }

        for (entry <- classItems) {
          tableRow {
            tableData(printClass(entry.clazz))
            tableData(out.println(entry.instCount))
            tableData(out.println(entry.totalSize))
            val instanceRetained = entry.totalRetained
            tableData("" + instanceRetained)
            val retainedInstSize = if(entry.instCount == 0) "-"
            else if(instanceRetained == 0) "0"
            else "" + instanceRetained/entry.instCount
            tableData("" + retainedInstSize)
          }
        }
      }
    }
  }
}