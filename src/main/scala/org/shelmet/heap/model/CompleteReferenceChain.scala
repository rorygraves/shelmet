package org.shelmet.heap.model

import org.shelmet.heap.HeapId


/**
 * A reference chain from a root to an object
 * @param root The root reference
 * @param chain The reference chain leading to this object
 */
class CompleteReferenceChain(val root : Root,val chain: List[HeapId]) {
  lazy val depth = chain.length
  def objectSet = chain.toSet
}
