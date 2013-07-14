package org.shelmet.heap.model

/**
 * Represents a chain of references to some target object
 */
class ReferenceChain(val obj: JavaHeapObject, val next: ReferenceChain) {

  val depth: Int = if (next == null) 1 else 1 + next.depth
}