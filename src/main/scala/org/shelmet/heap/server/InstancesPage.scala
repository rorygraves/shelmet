package org.shelmet.heap.server

import org.shelmet.heap.model.Snapshot
import org.eclipse.mat.snapshot.ISnapshot
import org.eclipse.mat.snapshot.model.{IObject, IClass}

class InstancesPage(oldSnapshot : Snapshot,snapshot : ISnapshot,query : String,includeSubclasses: Boolean) extends AbstractPage(snapshot) {

  override def run() {
    findObjectByQuery(query) match {
      case Some(clazz : IClass) =>

//        val oldClazz : JavaClass = oldSnapshot.findThing(HeapId(clazz.getObjectAddress),false).get.asInstanceOf[JavaClass]
        val title = if (includeSubclasses)
          "Instances of " + query + " (including subclasses)"
        else
          "Instances of " + query

        html(title) {
          out.print("<strong>")
          printClass(clazz)
          out.print("</strong><br/><br/>")
          clazz.getObjectIds
          val objects = clazz.getObjectIds(includeSubclasses).map(snapshot.getObject).sortWith( _.getObjectAddress < _.getObjectAddress)
          var totalSize: Long = 0
          var instances: Long = 0
          var i: Int = 0
          objects foreach {
            obj =>
              i += 1
              if (i % 1000 == 0) System.out.print(".")
              printThing(obj)
              out.println("<br/>")
              totalSize += obj.getUsedHeapSize
              instances += 1
          }
          h2(s"Total of $instances instances occupying $totalSize bytes.")
        }
      case Some(x : IObject) =>
        val text = s"Object ${x.getObjectAddress.toHexString} is not a class"
        html(text) {
          out.println(text)
        }
      case None =>
        val text = s"Object $query not found"
        html(text) {
          out.println(text)
        }
    }
  }
}