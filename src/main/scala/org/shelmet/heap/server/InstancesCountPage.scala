package org.shelmet.heap.server

import org.shelmet.heap.util.SortUtil
import scala.collection.SortedSet
import scala.math.Ordering
import org.eclipse.mat.snapshot.ISnapshot
import org.eclipse.mat.snapshot.model.IClass

class InstancesCountPage(snapshot : ISnapshot,excludePlatform: Boolean) extends AbstractPage(snapshot) {

  override def run() {
    val title = if (excludePlatform) "Instance Counts for All Classes (excluding platform)"
    else "Instance Counts for All Classes (including platform)"

    html(title) {
      val sortFn = SortUtil.sortByFn[(IClass,Int,Long)](
        (l,r)=>  r._2 - l._2,
        (l,r) => {
          val left = l._1.getName
          val right = r._1.getName
          if (left.endsWith("]") != right.endsWith("]")) {
            if (left.endsWith("]"))
              1
            else
              -1
          } else
            0
        },
        (l,r) => l._1.getName.compareTo(r._1.getName)
      )

      import scala.collection.JavaConversions._

      // use a sorted set so we build the ordering  during traversal rather than collecting then sorting
      val classesAndCounts = {

        val classes = if(excludePlatform)
          snapshot.getClasses.filterNot(_.isPlatformClass).toList
        else
          snapshot.getClasses.toList

        val classesAndC = classes.map { c => (c,c.getNumberOfObjects,snapshot.getHeapSize(c.getObjectIds)) }

        SortedSet.empty(Ordering fromLessThan sortFn) ++ classesAndC
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
        totalSize += clazzAndCount._3
      }
      h2(s"Total of $instances instances occupying $totalSize bytes.")
    }
  }
}