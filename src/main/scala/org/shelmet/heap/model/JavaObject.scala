package org.shelmet.heap.model

import org.shelmet.heap.parser.BlockDataReader
import org.shelmet.heap.HeapId

/**
 * Represents Java instance
 */
class JavaObject(id: HeapId, snapshotV: Snapshot,classId : HeapId, fieldReaderSrc: BlockDataReader)
  extends JavaHeapObject(id, snapshotV) {

  override def getClazz: JavaClass = classId.getOpt(snapshot).get.asInstanceOf[JavaClass]

  override def size: Int = snapshot.getMinimumObjectSize + fieldReaderSrc.length

  override def resolve(snapshot: Snapshot) {
    fields
    getClazz.addInstance(this)
  }

  private lazy val fields = parseFields()

  def getFieldsAndValues : List[(JavaField,Any)] = {
    getClazz.getFieldsForInstance.zip(fields)
  }

  def getField(name: String): Any = {
    getFieldsAndValues.find(_._1.name == name).getOrElse(
      throw new IllegalStateException("Field " + name + " not found on class " + getClazz.displayName))._2
  }

  override def visitReferencedObjects(visit : JavaHeapObject => Unit) {
    super.visitReferencedObjects(visit)

    fields.foreach {
      case f :JavaHeapObject => visit(f)
      case _ =>
    }
  }

  override def refersOnlyWeaklyTo(ss: Snapshot, other: JavaHeapObject): Boolean = {
    if (ss.weakReferenceClass != null && ss.weakReferenceClass.isAssignableFrom(getClazz)) {
      val referentFieldIndex = ss.referentFieldIndex
      !fields.zipWithIndex.exists { case (value,idx) => value == other && !(idx == referentFieldIndex) }
    }
    else
      false
  }

  /**
   * Describe the reference that this thing has to target.  This will only
   * be called if target is in the array returned by getChildrenForRootset.
   */
  override def describeReferenceTo(target: JavaHeapObject): List[String] = {
    val refs = getClazz.getFieldsForInstance.zip(fields).filter(_._2 == target).map( "field " + _._1.name)
    refs ::: super.describeReferenceTo(target)
  }

  private def parseFields(): Vector[Any] = {
    getClazz.readAllFields(heapId,fieldReaderSrc.getReader)
  }
}