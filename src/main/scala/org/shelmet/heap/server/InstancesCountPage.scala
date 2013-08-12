package org.shelmet.heap.server

import org.shelmet.heap.model.{JavaClass, Snapshot}
import org.shelmet.heap.util.SortUtil
import scala.collection.{SortedSet, SortedMap}
import scala.math.Ordering

class InstancesCountPage(snapshot : Snapshot,excludePlatform: Boolean) extends AbstractPage(snapshot) {

  override def run() {
    val title = if (excludePlatform) "Instance Counts for All Classes (excluding platform)"
    else "Instance Counts for All Classes (including platform)"

    html(title) {
      val sortfn = SortUtil.sortByFn[(JavaClass,Int)](
        (l,r)=>  r._2 - l._2,
        (l,r) => {
          val left = l._1.name
          val right = r._1.name
          if (left.startsWith("[") != right.startsWith("[")) {
            if (left.startsWith("["))
              1
            else
              -1
          } else
            0
        },
        (l,r) => l._1.name.compareTo(r._1.name)
      )

      // use a sorted set so we build the ordering  during traversal rather than collecting then sorting
      val classesAndCounts = {
        val classesAndC = (if(excludePlatform)
              snapshot.getClasses.filterNot(_.isPlatformClass)
            else
              snapshot.getClasses
          ).map( c => (c,c.getInstancesCount(includeSubclasses = false)))


        SortedSet.empty(Ordering fromLessThan sortfn) ++ classesAndC
      }

      var totalSize: Long = 0
      var instances: Long = 0
      for (clazzAndCount <- classesAndCounts) {
        val clazz = clazzAndCount._1
        val count = clazzAndCount._2
        printEncoded("" + count + " ")
        printAnchor("instances/" + encodeForURL(clazz),if (count == 1) "instance" else "instances")
        printEncoded(" of ")
        printClass(clazz)
        out.println("<br/>")
        instances += count
        totalSize += clazz.getTotalInstanceSize
      }
      h2(s"Total of $instances instances occupying $totalSize bytes.")
    }
  }
}