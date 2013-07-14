package org.shelmet.heap.model.create

import org.shelmet.heap.parser._
import org.shelmet.heap.model._
import org.shelmet.heap.HeapId

class ObjectPassDumpVisitor(snapshot : Snapshot,callStack: Boolean) extends AbstractDumpVisitor(callStack) {

  override def primitiveArray(id : HeapId,stackTraceSerialID : Int,primitiveSignature : Byte,elementSize : Int,data : AnyRef) {
    val objectSize = snapshot.getMinimumObjectSize + elementSize * data.asInstanceOf[Array[_]].length
    val va = new JavaValueArray(snapshot,id,objectSize,primitiveSignature,data)

    snapshot.addHeapObject(id, va)

    val stackTrace = getStackTraceFromSerial(stackTraceSerialID)
    snapshot.setSiteTrace(va, stackTrace)
  }

  override def instanceDump(id : HeapId,stackTraceSerialId : Int,classId : HeapId,bdr : BlockDataReader) {
    val jObj = new JavaObject(id,snapshot,classId,bdr)
    snapshot.addHeapObject(id, jObj)

    val stackTrace = getStackTraceFromSerial(stackTraceSerialId)
    snapshot.setSiteTrace(jObj, stackTrace)
  }

  override def objectArrayDump(id : HeapId,stackTraceSerialId : Int,numElements : Int,elementClassId : HeapId,
                               values : Seq[HeapId]) {
    val stackTrace = getStackTraceFromSerial(stackTraceSerialId)
    val arr = new JavaObjectArray(id,snapshot,elementClassId,values)
    snapshot.addHeapObject(id, arr)
    snapshot.setSiteTrace(arr, stackTrace)
  }
}
