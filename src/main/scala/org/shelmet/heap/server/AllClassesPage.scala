package org.shelmet.heap.server

import org.shelmet.heap.model.{JavaClass, Snapshot}
import org.shelmet.heap.util.Misc

class AllClassesPage(snapshot : Snapshot,excludePlatform: Boolean) extends AbstractPage(snapshot) {

  override def run() {
    val title = if(excludePlatform)
      "All Classes (excluding platform)"
    else
      "All Classes (including platform)"

    html(title) {
      val classes = if(excludePlatform)
        snapshot.getClasses.filterNot(_.isPlatformClass)
      else
        snapshot.getClasses

      val classesByPackage = classes.groupBy(_.getPackage).toList.sortBy(_._1)
      classesByPackage foreach { case (pkg,pkgClasses) =>
        h2(s"Package ${Misc.encodeHtml(pkg)}")
        ul[JavaClass](pkgClasses,{ clazz =>
          printClass(clazz)
          out.print(" [" + clazz.getIdString + "]")
        })
      }
    }
  }
}