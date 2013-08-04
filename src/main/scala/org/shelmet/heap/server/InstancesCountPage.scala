package org.shelmet.heap.server

import org.shelmet.heap.model.Snapshot
import org.shelmet.heap.util.{PlatformClasses, SortUtil}

class InstancesCountPage(snapshot : Snapshot,excludePlatform: Boolean) extends AbstractPage(snapshot) {

  override def run() {
    val title = if (excludePlatform) "Instance Counts for All Classes (excluding platform)"
    else "Instance Counts for All Classes (including platform)"

    html(title) {
      val classesL =
        if(excludePlatform)
          snapshot.getClasses.filterNot(PlatformClasses.isPlatformClass)
        else
          snapshot.getClasses

      val classes = classesL.toList.sortWith(SortUtil.sortByFn(
        (l,r)=>  r.getInstancesCount(includeSubclasses = false) - l.getInstancesCount(includeSubclasses = false),
        (l,r) => {
          val left = l.name
          val right = r.name
          if (left.startsWith("[") != right.startsWith("[")) {
            if (left.startsWith("["))
              1
            else
              -1
          } else
            0
        },
        (l,r) => l.name.compareTo(r.name)
      ))

      var totalSize: Long = 0
      var instances: Long = 0
      for (clazz <- classes) {
        val count: Int = clazz.getInstancesCount(includeSubclasses = false)
        printEncoded("" + count + " ")
        printAnchor("instances/" + encodeForURL(clazz),if (count == 1) "instance" else "instances")
        printEncoded(" of ")
        printClass(clazz)
        out.println("<br/>")
        instances += count
        totalSize += clazz.getTotalInstanceSize
      }
      out.println("<h2>Total of " + instances + " instances occupying " + totalSize + " bytes.</h2>")
    }
  }
}