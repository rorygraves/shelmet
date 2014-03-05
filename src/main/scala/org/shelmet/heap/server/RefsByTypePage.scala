package org.shelmet.heap.server

import scala.collection._
import org.eclipse.mat.snapshot.ISnapshot
import org.eclipse.mat.snapshot.model.{IObject, IClass}
import org.shelmet.heap.util.Misc

/**
 * References by type summary
 */
class RefsByTypePage(snapshot : ISnapshot,query : String) extends AbstractPage(snapshot) {
  override def run() {
    findObjectByQuery(query) match {
      case Some(clazz : IClass) =>
        val referrersStat = mutable.Map[IObject,Long]()
        val refereesStat = mutable.Map[IObject,Long]()

        clazz.getObjectIds foreach { instId =>
          snapshot.getInboundRefererIds(instId) foreach { iRefId =>
            val cl = snapshot.getObject(iRefId).getClazz
            referrersStat.put(cl,referrersStat.getOrElse(cl,0L)+1)
          }

          snapshot.getOutboundReferentIds(instId) foreach { oRefId =>
            val cl = snapshot.getObject(oRefId).getClazz
            refereesStat.put(cl,refereesStat.getOrElse(cl,0L)+1)
          }
        }

        html("References by Type") {
          out.println("<p>")
          printClass(clazz)
          out.println("[" + clazz.getObjectId + "]")
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
      case Some(_ : IObject) =>
        val text = query + " is not a class object"
        html("References by Type") {
          out.println(text)
        }
      case None =>
        val text = "class not found: " + query
        html("References by Type") {
          out.println(text)
        }
    }
  }

  private def print(map: Map[IObject, Long]) {
    val sortedPairs = map.toList.sortBy(_._2)

    table {
      out.println("<tr><th>Class</th><th>Count</th></tr>")
      for ((clazz,count) <- sortedPairs) {
        tableRow {
          tableData {
            printAnchor("refsByType/" + Misc.toHex(clazz.getObjectAddress),clazz.getNewDisplayName)
          }
          tableData(out.println(count))
        }
      }
    }
  }
}