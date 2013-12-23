package org.shelmet.heap.model

import org.shelmet.heap.HeapId
import org.shelmet.heap.shared.InstanceId

/**
 * Represents Java instance
 * @param fieldsLength The length of the field values (in bytes)
 */
class JavaObject(id: HeapId, snapshotV: Snapshot,val instanceId : InstanceId,classId : HeapId,
                 val fieldValues : Vector[Any],fieldsLength : Int) extends JavaHeapObject(id,Some(instanceId),snapshotV) {

  var minDepth = -1
  var maxDepth = -1

  override def getClazz: JavaClass = classId.get.asInstanceOf[JavaClass]

  override def size: Int = Snapshot.instance.getMinimumObjectSize + fieldsLength

  override def resolve(snapshot: Snapshot) {
    resolvedFields.length // force the resolution of heap objects
    getClazz.addInstance(this)
  }

  private def resolvedFields = fieldValues.map {
    case id : HeapId => id.getOpt.getOrElse(null)
    case x => x
  }

  def getFieldsAndValues : List[(JavaField,Any)] = {
    getClazz.getFieldsForInstance.zip(resolvedFields)
  }

  def getField(name: String): Any = {
    getFieldsAndValues.find(_._1.name == name).getOrElse(
      throw new IllegalStateException("Field " + name + " not found on class " + getClazz.displayName))._2
  }

  override def visitReferencedObjects(visit : JavaHeapObject => Unit,includeStatics : Boolean = true) {
    super.visitReferencedObjects(visit,includeStatics)

    fieldValues.foreach {
      case h : HeapId if !h.isNull=> visit(h.getOpt.get)
      case _ =>
    }
  }

  lazy val isWeakRefClazz : Boolean = Snapshot.instance.weakReferenceClass != null &&
    Snapshot.instance.weakReferenceClass.isAssignableFrom(getClazz)

  override def refersOnlyWeaklyTo(other: JavaHeapObject): Boolean = {
    if (isWeakRefClazz) {
      val referentFieldIndex = Snapshot.instance.referentFieldIndex
      val size = fieldValues.size
      var idx = 0
      while(idx < size) {
        if(fieldValues(idx) == other.heapId && !(idx == referentFieldIndex))
          return false
        idx += 1
      }
      true
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