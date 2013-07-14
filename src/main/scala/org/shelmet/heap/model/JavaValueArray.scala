package org.shelmet.heap.model

import org.shelmet.heap.HeapId

class JavaValueArray(snapshot : Snapshot,id: HeapId,val size : Int,sig : Byte,val data : AnyRef) extends JavaHeapObject(id,snapshot) {

  def elementType: Byte = sig

  private lazy val clazzId: HeapId = snapshot.getArrayClass("" + elementType.asInstanceOf[Char]).heapId

  override def getClazz: JavaClass = clazzId.getOpt(snapshot).get.asInstanceOf[JavaClass]

  override def resolve(snapshot: Snapshot) {
    getClazz.addInstance(this)
  }

  def valueString(bigLimit: Boolean=true): String = {

    var result: StringBuffer = null
    val elementSignature: Byte = elementType
    if (elementSignature == 'C') {
      new String(data.asInstanceOf[Array[Char]])
    } else {
      val data = this.data
      val limit = if(bigLimit) 1000 else 8
      result = new StringBuffer("{")
      val arr = data.asInstanceOf[Array[_]]
      val length = arr.length

      var i: Int = 0
      while (i < length) {
        if (i > 0) {
          result.append(", ")
        }

        if (i >= limit) {
          result.append("... ")
          i = length // break out of the loop
        } else {
          elementSignature match {
            case 'Z' =>
              val bVal = data.asInstanceOf[Array[Boolean]](i)
              if (bVal)
                result.append("true")
              else
                result.append("false")
            case 'B' =>
              val bVal = data.asInstanceOf[Array[Byte]](i)
              result.append("0x%02X".format(bVal))
            case 'S' =>
              val sVal = data.asInstanceOf[Array[Short]](i)
              result.append(sVal)
            case 'I' =>
              val iVal = data.asInstanceOf[Array[Int]](i)
              result.append(iVal)
            case 'J' =>
              val lVal = data.asInstanceOf[Array[Long]](i)
              result.append(lVal)
            case 'F' =>
              val fVal = data.asInstanceOf[Array[Float]](i)
              result.append(fVal)
            case 'D' =>
              val dVal = data.asInstanceOf[Array[Double]](i)
              result.append(dVal)
            case _ => {
              throw new RuntimeException("unknown primitive type?")
            }
          }
        }
        i += 1
      }
      result.append("}")
      result.toString
    }
  }
}