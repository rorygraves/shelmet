package org.shelmet.heap.model

import org.shelmet.heap.HeapId

/**
 * An as yet unrecognized heap item (or missing from the dump)
 */
class UnknownHeapObject(id : HeapId,snapshot : Snapshot) extends JavaHeapObject(id,snapshot) {
  override def getClazz: JavaClass = snapshot.javaLangObjectClass

  override def toString: String = "Unknown Heap Object" + "@" + getIdString
}
