package org.shelmet.heap

import org.shelmet.heap.model.{JavaHeapObject, Snapshot}

case class HeapId(id : Long) extends Ordered[HeapId] {
  def toHex = "0x%x".format(id)

  def compare(that: HeapId): Int = id.compareTo(that.id)

  def isNull = id == 0
  def getOpt : Option[JavaHeapObject] = Snapshot.instance.findHeapObject(this)
  def get : JavaHeapObject = getOpt.get
}
