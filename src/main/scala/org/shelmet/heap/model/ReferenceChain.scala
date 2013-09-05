package org.shelmet.heap.model


/**
 * A reference chain from a root to an object
 * @param root The root reference
 * @param chain The reference chain leading to this object
 */
class CompleteReferenceChain(val root : Root,val chain: List[JavaHeapObject]) {
  lazy val depth = chain.length
  def objectSet = chain.toSet
}
