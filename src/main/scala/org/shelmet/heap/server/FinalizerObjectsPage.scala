package org.shelmet.heap.server

import org.eclipse.mat.snapshot.ISnapshot
import org.eclipse.mat.snapshot.model.{IObject, IInstance, ObjectReference, IClass}

object FinalizerObjectsPage {
  def getFinalizerObjects(snapshot : ISnapshot) : List[IObject] = {

    import scala.collection.JavaConversions._
    val classes = snapshot.getClassesByName("java.lang.ref.Finalizer",false).toList
    classes match {
      case (clazz : IClass) :: Nil =>
        var res : List[IObject] = Nil
        val queueField = clazz.getStaticFields.find(_.getName == "queue").getOrElse(throw new IllegalStateException("Unable to find finaliser queue"))
        val queueObj = queueField.getValue.asInstanceOf[ObjectReference].getObject.asInstanceOf[IInstance]
        var queueHead = queueObj.getField("head").getValue.asInstanceOf[ObjectReference]
        while(queueHead != null) {
          val queueObj = queueHead.getObject.asInstanceOf[IInstance]

          val referent = queueObj.getField("referent").getValue.asInstanceOf[ObjectReference]
          if(referent != null)
            res = referent.getObject :: res

          val nextVal = queueObj.getField("next").getValue
          queueHead = if(nextVal == null) null else nextVal.asInstanceOf[ObjectReference]
        }
        res
      case _ =>
        throw new IllegalStateException("Cant find Finalizer class")
    }
  }
}

class FinalizerObjectsPage(snapshot : ISnapshot) extends AbstractPage(snapshot) {

  override def run() {
    html("Objects pending finalization") {
      out.println("<a href='/finalizerSummary/'>Finalizer summary</a>")
      out.println("<h1>Objects pending finalization</h1>")
      FinalizerObjectsPage.getFinalizerObjects(snapshot).foreach { obj =>
        printThing(obj)
        out.println("<br/>")
      }
    }
  }


}