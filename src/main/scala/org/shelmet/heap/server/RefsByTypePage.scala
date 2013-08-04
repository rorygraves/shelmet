package org.shelmet.heap.server

import org.shelmet.heap.model.{JavaHeapObject, Snapshot, JavaClass}
import scala.collection._

/**
 * References by type summary
 */
class RefsByTypePage(snapshot : Snapshot,query : String) extends AbstractPage(snapshot) {
  override def run() {
    findObjectByQuery(query) match {
      case Some(clazz : JavaClass) =>
        val referrersStat = mutable.Map[JavaClass,Long]()
        val refereesStat = mutable.Map[JavaClass,Long]()

        clazz.getInstances(includeSubclasses = false) foreach {
          instance =>
            if (instance.heapId.id != -1) {
              instance.referers foreach {
                ref =>
                  val cl: JavaClass = ref.getClazz
                  if (cl == null)
                    System.out.println("null class for " + ref)
                  else
                    referrersStat.put(cl,referrersStat.getOrElse(cl,0L)+1)
              }
              instance.visitReferencedObjects{ obj =>
                val cl: JavaClass = obj.getClazz
                refereesStat.put(cl,refereesStat.getOrElse(cl,0L)+1)
              }
            }
        }

        html("References by Type") {
          out.println("<p>")
          printClass(clazz)
          if (clazz.heapId.id != -1) {
            out.println("[" + clazz.getIdString + "]")
          }
          out.println("</p>")
          if (referrersStat.size != 0) {
            out.println("<h3>Referrers by Type</h3>")
            print(referrersStat)
          }
          if (refereesStat.size != 0) {
            out.println("<h3>Referees by Type</h3>")
            print(refereesStat)
          }
        }
      case Some(_ : JavaHeapObject) =>
        val text = query + " is not a class object"
        html(text) {
          out.println(text)
        }
      case None =>
        val text = "class not found: " + query
        html(text) {
          out.println(text)
        }
    }
  }

  private def print(map: Map[JavaClass, Long]) {
    val sortedPairs = map.toList.sortBy(_._2)

    table {
      out.println("<tr><th>Class</th><th>Count</th></tr>")
      for ((clazz,count) <- sortedPairs) {
        tableRow {
          tableData {
            printAnchor("refsByType/" + clazz.getIdString,clazz.name)
          }
          tableData(out.println(count))
        }
      }
    }
  }
}