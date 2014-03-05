package org.shelmet.heap.server

import org.shelmet.heap.model.Snapshot
import org.eclipse.mat.snapshot.ISnapshot
import org.eclipse.mat.snapshot.model.{GCRootInfo, IObject}

/**
 * Query to show the StackTrace for a given root
 */
class RootStackPage(oldSnapshot : Snapshot,snapshot : ISnapshot,query : String) extends AbstractPage(snapshot) {

  override def run() {
    query.split(":").toList match {
      case (id : String) :: (indexStr : String) :: Nil =>
        val targetObj = super.findObjectByQuery(query)
        targetObj match {
          case None =>
            html("Root not found") {
              out.println("Root at " + query + " not found")
            }
          case Some(obj : IObject) =>
            val index = Integer.parseInt(indexStr)
            val roots = obj.getGCRootInfo
            if(index < roots.size) {
              val root = roots(index)
              html(s"Stack Trace for ${GCRootInfo.getTypeAsString(root.getType)}($index)") {
                out.println("<p>")
                if(root.getContextAddress != 0) {
                  val trace = snapshot.getThreadStack(snapshot.mapAddressToId(root.getContextAddress))
                  if(trace == null)
                    out.println("No trace available")
                  else
                    printStackTrace(trace)
                } else {
                  out.println("No context thread")
                }
                out.println("</p>")
              }
            } else {
              html("Root not found") {
                out.println("Root at " + index + " not found")
              }
            }
        }
      case _ =>
        html("Root not valid - form address:index") {
          out.println("Root Root not valid - form address:index")
        }
    }
  }
}