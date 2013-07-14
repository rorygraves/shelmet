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

  lazy val fields = parseFields()

  def getFields: Vector[Any] = fields

  def getField(name: String): Any = {
    val instFields = getClazz.getFieldsForInstance

    val idx = instFields.indexWhere(_.name == name)
    if (idx == -1)
      throw new IllegalStateException("Field " + name + " not found on class " + getClazz.displayName)
    else
      fields(idx)
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
    var refs = fields.zipWithIndex.filter{ case (value,idx) => value == target }.map {
      case (value,idx) => "field "  + getClazz.getFieldForInstance(idx).name
    }.toList

    refs :::= super.describeReferenceTo(target)
    refs
  }

  private def parseFields(): Vector[Any] = {
    getClazz.readAllFields(fieldReaderSrc.getReader)
  }
}