package org.shelmet.heap.model

import org.shelmet.heap.HeapId

class JavaObjectArray(id : HeapId,snapshot : Snapshot,classId: HeapId,
                      elementIDs : Seq[HeapId]) extends JavaHeapObject(id,snapshot) {

  override def getClazz: JavaClass = classId.get.asInstanceOf[JavaClass]

  lazy val elements : List[JavaHeapObject] = elementIDs.map(hid => snapshot.findHeapObject(hid).getOrElse(null)).toList

  override def resolve(snapshot: Snapshot) {
    // trigger lazy evaluation
    elements.size
  }

  def displayName : String = getClazz.displayName

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