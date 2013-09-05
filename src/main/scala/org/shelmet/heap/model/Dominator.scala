package org.shelmet.heap.model

import org.shelmet.heap.HeapId

sealed trait Dominator

case object UnknownDominator extends Dominator
case object NoDominotor extends Dominator
case class HeapObjectDominator(heapId : HeapId) extends Dominator