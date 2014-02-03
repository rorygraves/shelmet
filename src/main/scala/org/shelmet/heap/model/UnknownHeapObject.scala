package org.shelmet.heap.model

import org.shelmet.heap.HeapId

/**
 * An as yet unrecognized heap item (or missing from the dump)
 */
class UnknownHeapObject(id : HeapId) extends JavaHeapObject(id,None) {
  override def getClazz: JavaClass = Snapshot.instance.javaLangObjectClass

  override def size: Int = 0

  override def resolve(snapshot: Snapshot) {
    getClazz.addInstance(this)
  }

  override def toString: String = "Unknown Heap Object" + "@" + getIdString
}
