package org.shelmet.heap.server

import org.shelmet.heap.model.{JavaHeapObject, Snapshot, JavaClass}

class InstancesPage(snapshot : Snapshot,query : String,includeSubclasses: Boolean) extends AbstractPage(snapshot) {

  override def run() {
    findObjectByQuery(query) match {
      case Some(clazz : JavaClass) =>

        val title = if (includeSubclasses)
          "Instances of " + query + " (including subclasses)"
        else
          "Instances of " + query

        html(title) {
          out.print("<strong>")
          printClass(clazz)
          out.print("</strong><br/><br/>")
          val objects = clazz.getInstances(includeSubclasses).sortWith( _.getIdString < _.getIdString)
          var totalSize: Long = 0
          var instances: Long = 0
          var i: Int = 0
          objects foreach {
            obj =>
              i += 1
              if (i % 1000 == 0) System.out.print(".")
              printThing(obj)
              out.println("<br/>")
              totalSize += obj.size
              instances += 1
          }
          out.println("<h2>Total of " + instances + " instances occupying " + totalSize + " bytes.</h2>")
        }
      case Some(x : JavaHeapObject) =>
        val text = s"Object ${x.getIdString} is not a class"
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