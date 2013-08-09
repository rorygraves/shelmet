package org.shelmet.heap.model

import org.shelmet.heap.HeapId

/**
 * Represents the value of a static field of a JavaClass
 */
class JavaStatic(val snapshot : Snapshot,val field: JavaField,private val value: Any) {

  def resolve(clazz: JavaClass, snapshot: Snapshot) {
    value match {
      case heapRef : HeapId =>
        if (!clazz.loader.isDefined) {
          snapshot.addRoot(new Root(snapshot,heapRef, clazz.heapId, Root.JAVA_STATIC,
            "Static reference from " + clazz.name + "." + field.name ))
        }
      case _ =>
    }
  }

  def getValue: Any = value match {
    case h : HeapId => snapshot.findHeapObject(h).getOrElse(null)
    case other => other
  }
}