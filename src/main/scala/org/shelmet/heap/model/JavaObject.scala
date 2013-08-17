package org.shelmet.heap.model

import org.shelmet.heap.HeapId

/**
 * Represents Java instance
 * @param fieldsLength The length of the field values (in bytes)
 */
class JavaObject(id: HeapId, snapshotV: Snapshot,classId : HeapId,val fieldValues : Vector[Any],fieldsLength : Int)
  extends JavaHeapObject(id, snapshotV) {

  var minDepth = -1
  var maxDepth = -1

  override def getClazz: JavaClass = classId.getOpt(snapshot).get.asInstanceOf[JavaClass]

  override def size: Int = snapshot.getMinimumObjectSize + fieldsLength

  override def resolve(snapshot: Snapshot) {
    resolvedFields.length // force the resolution of heap objects
    getClazz.addInstance(this)
  }

  private def resolvedFields = fieldValues.map( _ match {
    case id : HeapId =>
        if(id.isNull)
          null
        else
          snapshot.findHeapObject(id).get
    case x => x
  })

  def getFieldsAndValues : List[(JavaField,Any)] = {
    getClazz.getFieldsForInstance.zip(resolvedFields)
  }

  def getField(name: String): Any = {
    getFieldsAndValues.find(_._1.name == name).getOrElse(
      throw new IllegalStateException("Field " + name + " not found on class " + getClazz.displayName))._2
  }

  override def visitReferencedObjects(visit : JavaHeapObject => Unit) {
    super.visitReferencedObjects(visit)

    fieldValues.foreach {
      case h : HeapId if !h.isNull=> visit(h.getOpt.get)
      case _ =>
    }
  }

  override def refersOnlyWeaklyTo(ss: Snapshot, other: JavaHeapObject): Boolean = {
    if (ss.weakReferenceClass != null && ss.weakReferenceClass.isAssignableFrom(getClazz)) {
      val referentFieldIndex = ss.referentFieldIndex
      !fieldValues.zipWithIndex.exists { case (value,idx) => value == other.heapId && !(idx == referentFieldIndex) }
    }
    else
      false
  }

  /**
   * Describe the reference that this thing has to target.  This will only
   * be called if target is in the array returned by getChildrenForRootset.
   */
  override def describeReferenceTo(target: JavaHeapObject): List[String] = {
    val refs = getClazz.getFieldsForInstance.zip(fieldValues).filter(_._2 == target.heapId).map( "field " + _._1.name)
    refs ::: super.describeReferenceTo(target)
  }
}