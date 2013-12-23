package org.shelmet.shared.dominator

trait DominatorNode[T <: DominatorNode[T] ] { self : T =>
  def upstreamNodes : Set[T]
  def downstreamNodes : Set[T]
}
