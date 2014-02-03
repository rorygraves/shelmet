package org.shelmet.heap.model.create

import org.shelmet.heap.model._
import org.shelmet.heap.HeapId
import org.shelmet.heap.shared.{BaseFieldType, FieldType}

class ObjectPassDumpVisitor(snapshot : Snapshot,callStack: Boolean) extends AbstractDumpVisitor(callStack) {

  override def primitiveArray(heapId : HeapId,stackTraceSerialID : Int,fieldType : BaseFieldType,data : Seq[AnyVal]) {
    val objectSize = snapshot.getMinimumObjectSize + fieldType.fieldSize * data.size

    val classId = snapshot.getPrimitiveArrayClass(fieldType).heapId
    val instanceId = getInstanceId(classId)

    val va = new JavaValueArray(heapId,instanceId,classId,objectSize,fieldType,data)
    snapshot.addHeapObject(heapId, va)

    val stackTrace = getStackTraceFromSerial(stackTraceSerialID)
    snapshot.setSiteTrace(va, stackTrace)
  }

  override def getClassFieldInfo(classHeapId : HeapId) : Option[List[FieldType]] = {
    snapshot.findHeapObject(classHeapId) match {
      case Some(clazz : JavaClass) => Some(clazz.getFieldsForInstance.map(_.fieldType))
      case Some(other : JavaHeapObject) => throw new IllegalStateException("Requested class id " + classHeapId + "not correct type")
      case None => None
    }
  }

  override def instanceDump(heapId : HeapId,stackTraceSerialId : Int,classId : HeapId,fields : Option[Vector[Any]],fieldsLengthBytes : Int) {
    if(!fields.isDefined)
      throw new IllegalStateException("Fields must be defined for instance declaration")
    val instanceId = getInstanceId(classId)
    val jObj = new JavaObject(heapId,snapshot,instanceId,classId,fields.get,fieldsLengthBytes)
    snapshot.addHeapObject(heapId, jObj)

    val stackTrace = getStackTraceFromSerial(stackTraceSerialId)
    snapshot.setSiteTrace(jObj, stackTrace)
  }

  override def objectArrayDump(heapId : HeapId,stackTraceSerialId : Int,numElements : Int,classId : HeapId,
                               values : Seq[HeapId]) {
    val stackTrace = getStackTraceFromSerial(stackTraceSerialId)
    val instanceId = getInstanceId(classId)
    val arr = new JavaObjectArray(heapId,instanceId,classId,values)
    snapshot.addHeapObject(heapId, arr)
    snapshot.setSiteTrace(arr, stackTrace)
  }
}
