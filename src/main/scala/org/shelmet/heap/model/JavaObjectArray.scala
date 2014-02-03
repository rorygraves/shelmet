package org.shelmet.heap.model

import org.shelmet.heap.HeapId
import org.shelmet.heap.shared.InstanceId

class JavaObjectArray(id : HeapId,val instanceId : InstanceId,classId: HeapId,
                      elementIDs : Seq[HeapId]) extends JavaHeapObject(id,Some(instanceId)) {

  override def getClazz: JavaClass = classId.get.asInstanceOf[JavaClass]

  override def size: Int = Snapshot.instance.getMinimumObjectSize + elementIDs.length * Snapshot.instance.identifierSize

  lazy val elements : List[JavaHeapObject] = elementIDs.map(hid => Snapshot.instance.findHeapObject(hid).getOrElse(null)).toList

  override def resolve(snapshot: Snapshot) {
    getClazz.addInstance(this)

    // trigger lazy evaluation
    elements.size
  }

  def displayName : String = getClazz.displayName

  override def visitReferencedObjects(visit : JavaHeapObject => Unit,includeStatics : Boolean = true) {
    super.visitReferencedObjects(visit,includeStatics)
    for (element <- elements) element match {
      case jho : JavaHeapObject =>
        visit(jho)
      case _ =>
    }
  }

  /**
   * Describe the reference that this thing has to target.  This will only
   * be called if target is in the array returned by getChildrenForRootset.
   */
  override def describeReferenceTo(target: JavaHeapObject): List[String] = {
    var refs = elements.zipWithIndex.filter(_._1 == target).map { itemIndex =>
      "element " + itemIndex._2
    }

    refs :::= super.describeReferenceTo(target)
    refs
  }
}