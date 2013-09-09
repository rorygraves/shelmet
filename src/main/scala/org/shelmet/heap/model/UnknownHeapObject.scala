package org.shelmet.heap.model

import org.shelmet.heap.HeapId

/**
 * An as yet unrecognized heap item (or missing from the dump)
 */
class UnknownHeapObject(id : HeapId,snapshot : Snapshot) extends JavaHeapObject(id,None,snapshot) {
  override def getClazz: JavaClass = snapshot.javaLangObjectClass

  override def size: Int = 0

  override def resolve(snapshot: Snapshot) {
    getClazz.addInstance(this)
  }

  override def toString: String = "Unknown Heap Object" + "@" + getIdString
}
