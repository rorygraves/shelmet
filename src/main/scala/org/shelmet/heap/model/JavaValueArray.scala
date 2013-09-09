package org.shelmet.heap.model

import org.shelmet.heap.shared._
import org.shelmet.heap.HeapId

class JavaValueArray(heapId: HeapId,snapshot : Snapshot,val instanceId : InstanceId,
                     val clazzId : HeapId,val size : Int,fieldType : FieldType,
                     val data : Seq[AnyVal]) extends JavaHeapObject(heapId,Some(instanceId),snapshot) {

  override def getClazz: JavaClass = clazzId.getOpt(snapshot).get.asInstanceOf[JavaClass]

  override def resolve(snapshot: Snapshot) {
    getClazz.addInstance(this)
  }

  def valueString(bigLimit: Boolean=true): String = {
    if (fieldType == CharFieldType)
      new String(data.asInstanceOf[Seq[Char]].toArray)
    else {
      val result = new StringBuilder("{")
      val limit = if(bigLimit) 1000 else 8

      data.take(limit).map {
        case b : Boolean => if (b) "true" else "false"
        case byte : Byte => "0x%02X".format(byte)
        case x => x.toString
      }.addString(result,", ")
      if(data.size > limit)
        result.append("... ")
      result.append("}")
      result.toString()
    }
  }
}