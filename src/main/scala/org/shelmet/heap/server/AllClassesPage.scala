package org.shelmet.heap.server

import org.shelmet.heap.model.Snapshot
import org.shelmet.heap.util.{PlatformClasses, Misc}

class AllClassesPage(snapshot : Snapshot,excludePlatform: Boolean) extends AbstractPage(snapshot) {

  override def run() {
    val title = if(excludePlatform)
      "All Classes (excluding platform)"
    else
      "All Classes (including platform)"

    html(title) {
      val classes = if(excludePlatform)
        snapshot.getClasses.filterNot(PlatformClasses.isPlatformClass)
      else
        snapshot.getClasses

      val classesByPackage = classes.groupBy(_.getPackage).toList.sortBy(_._1)
      classesByPackage foreach { case (pkg,pkgClasses) =>
        out.println(s"<h2>Package ${Misc.encodeHtml(pkg)}</h2>")
        out.println("<ul>")

        pkgClasses foreach { clazz =>
          out.println("<li>")
          printClass(clazz)
          if (clazz.heapId.id != -1) {
            out.print(" [" + clazz.getIdString + "]")
          }
          out.println("</li>")
        }
        out.println("</ul>")
      }
    }

  }
}