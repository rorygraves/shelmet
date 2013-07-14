package org.shelmet.heap

import org.shelmet.heap.model.{JavaHeapObject, Snapshot}

case class HeapId(id : Long) extends AnyVal {
  def toHex = "0x%x".format(id)

  def isNull = id == 0
  def getOpt(implicit snapshot : Snapshot) : Option[JavaHeapObject] = snapshot.findHeapObject(this)
}
