package org.shelmet.heap.model

import org.shelmet.heap.shared._
import org.shelmet.heap.HeapId

class JavaValueArray(heapId: HeapId,snapshot : Snapshot,
                     val clazzId : HeapId,fieldType : FieldType) extends JavaHeapObject(heapId,snapshot) {

  override def getClazz: JavaClass = clazzId.getOpt.get.asInstanceOf[JavaClass]
}