package org.shelmet.heap.model

import org.shelmet.heap.HeapId

class JavaObjectArray(id : HeapId,snapshot : Snapshot,classId: HeapId,elementIDs : Seq[HeapId]) extends JavaHeapObject(id,snapshot) {

  override def getClazz: JavaClass = classId.getOpt(snapshot).get.asInstanceOf[JavaClass]

  override def size: Int = snapshot.getMinimumObjectSize + elementIDs.length * snapshot.identifierSize

  lazy val elements : List[JavaHeapObject] = elementIDs.map(hid => snapshot.findHeapObject(hid).getOrElse(null)).toList

  override def resolve(snapshot: Snapshot) {
    getClazz.addInstance(this)

    // trigger lazy evaluation
    elements.size
  }

  def displayName : String = getClazz.displayName

  override def visitReferencedObjects(visit : JavaHeapObject => Unit) {
    super.visitReferencedObjects(visit)
    for (element <- elements) {
      element match {
        case jho : JavaHeapObject =>
          visit(jho)
        case _ =>
      }
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