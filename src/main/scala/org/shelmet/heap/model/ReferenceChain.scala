package org.shelmet.heap.model


// TODO use a class rather than null
/**
 * Represents a chain of references to some target object
 */
class ReferenceChain(val obj: JavaHeapObject, val next: ReferenceChain) {

  val depth: Int = if (next == null) 1 else 1 + next.depth
}

/**
 * A reference change that is complete with an actual root
 * @param root The root reference
 * @param chain The reference chain leading to this object
 */
class CompleteReferenceChain(val root : Root,val chain: ReferenceChain) {
  val depth = chain.depth
}
