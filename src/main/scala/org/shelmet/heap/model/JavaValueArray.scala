package org.shelmet.heap.model

import org.shelmet.heap.shared._
import org.shelmet.heap.HeapId

class JavaValueArray(heapId: HeapId,snapshot : Snapshot,val instanceId : InstanceId,
                     val clazzId : HeapId,val size : Int,
                     fieldType : FieldType) extends JavaHeapObject(heapId,Some(instanceId),snapshot) {

  override def getClazz: JavaClass = clazzId.getOpt.get.asInstanceOf[JavaClass]

  override def resolve(snapshot: Snapshot) {
    getClazz.addInstance(this)
  }
}