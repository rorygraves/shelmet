package org.shelmet.heap.server

import org.shelmet.heap.util.Misc
import org.eclipse.mat.snapshot.ISnapshot
import org.eclipse.mat.snapshot.model.IClass

class AllClassesPage(snapshot : ISnapshot,excludePlatform: Boolean) extends AbstractPage(snapshot) {

  override def run() {
    val title = if(excludePlatform)
      "All Classes (excluding platform)"
    else
      "All Classes (including platform)"

    import scala.collection.JavaConversions._
    html(title) {
      val classes = if(excludePlatform)
        snapshot.getClasses.filterNot(_.isPlatformClass)
      else
        snapshot.getClasses.toList

      val classesByPackage = classes.groupBy(_.getPackage).toList.sortBy(_._1)
      classesByPackage foreach { case (pkg,pkgClasses) =>
        h2(s"Package ${Misc.encodeHtml(pkg)}")
        ul[IClass](pkgClasses,{ clazz =>
          printClass(clazz)
          out.print(" [" + Misc.toHex(clazz.getObjectAddress) + "]")
        })
      }
    }
  }
}