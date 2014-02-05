package org.shelmet.heap.model

import org.shelmet.heap.shared._
import org.shelmet.heap.HeapId
import org.shelmet.heap.parser.ArrayWrapper

class JavaValueArray(heapId: HeapId,val instanceId : InstanceId,
                     val clazzId : HeapId,val size : Int,fieldType : FieldType,
                     val data : ArrayWrapper) extends JavaHeapObject(heapId,Some(instanceId)) {

  override def getClazz: JavaClass = clazzId.getOpt.get.asInstanceOf[JavaClass]

  override def resolve(snapshot: Snapshot) {
    getClazz.addInstance(this)
  }

  def valueString(bigLimit: Boolean=true): String = {
    if (fieldType == CharFieldType)
      data.toString
//      new String(data.asInstanceOf[Seq[Char]].toArray)
    else {
      val result = new StringBuilder("{")
      val limit = if(bigLimit) 1000 else 8

      data.asSeq.take(limit).map {
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