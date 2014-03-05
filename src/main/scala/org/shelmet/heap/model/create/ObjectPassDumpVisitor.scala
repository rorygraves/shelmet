package org.shelmet.heap.model.create

import org.shelmet.heap.model._
import org.shelmet.heap.HeapId
import org.shelmet.heap.shared.{BaseFieldType, FieldType}

class ObjectPassDumpVisitor(snapshot : Snapshot,callStack: Boolean) extends AbstractDumpVisitor(callStack) {

  override def primitiveArray(heapId : HeapId,fieldType : BaseFieldType,data : Seq[AnyVal]) {

    // we don't use the data field right now

    val classId = snapshot.getPrimitiveArrayClass(fieldType).heapId

    val va = new JavaValueArray(heapId,snapshot,classId,fieldType)
    snapshot.addHeapObject(heapId, va)
  }

  override def getClassFieldInfo(classHeapId : HeapId) : Option[List[FieldType]] = {
    snapshot.findHeapObject(classHeapId) match {
      case Some(clazz : JavaClass) => Some(clazz.getFieldsForInstance.map(_.fieldType))
      case Some(other : JavaHeapObject) => throw new IllegalStateException("Requested class id " + classHeapId + "not correct type")
      case None => None
    }
  }

  override def instanceDump(heapId : HeapId,classId : HeapId,fields : Option[Vector[Any]],fieldsLengthBytes : Int) {
    if(!fields.isDefined)
      throw new IllegalStateException("Fields must be defined for instance declaration")
    val jObj = new JavaObject(heapId,snapshot,classId,fields.get,fieldsLengthBytes)
    snapshot.addHeapObject(heapId, jObj)
  }

  override def objectArrayDump(heapId : HeapId,numElements : Int,classId : HeapId,
                               values : Seq[HeapId]) {
    val arr = new JavaObjectArray(heapId,snapshot,classId,values)
    snapshot.addHeapObject(heapId, arr)
  }
}
