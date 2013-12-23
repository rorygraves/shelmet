package org.shelmet.shared.dominator

import scala.collection.mutable.ListBuffer
import scala.annotation.tailrec

class DominatorCalculator[E <: DominatorNode[E]]  {
  def calculateDominators(graph : Set[E]) : Map[E,Option[E]] = {
    var remaining = graph

    var results : Map[E,Option[E]] = Map.empty

    println("Graph size = " + graph.size)

    val nodeDepths = depths(graph)

    println("Node depths = " + nodeDepths.size)
    // deal with the simple cases - roots and simple children
    remaining foreach { e =>
      if(e.upstreamNodes.isEmpty) {
        remaining -= e
        results += (e -> None)
      }
    }

    remaining.toList.filter(nodeDepths(_) >=0).sortBy(nodeDepths).foreach { node =>
      val iDom = calculateIDom(node,results)
      results += (node -> iDom)
     }

    results
  }

  def calculateIDom(node : E,currentDoms : Map[E,Option[E]] ) : Option[E] = {
    val chains = referenceChainsFor(node,currentDoms)

    val sharedSet : Set[E] = if(chains.isEmpty) Set.empty else {
      chains.map(_.toSet).reduce(_.intersect(_)) - node
    }
    if(sharedSet.isEmpty)
      None
    else {
      val sharedDoms = chains.head.reverse.dropWhile(!sharedSet.contains(_))
      Some(sharedDoms.head)
    }
  }

  /**
   * Calculate the depth from root node for all nodes (a root node is depth 0)
   * @param graph The graph of nodes
   * @return a map of node to depth from closest root
   */
  def depths(graph : Set[E]) : Map[E,Int] = {
    var curLevel: Set[E] = graph.filter(_.upstreamNodes.isEmpty).toSet
    println("Root nodes = " + curLevel.size)
    var nextLevel : Set[E] = Set.empty
    var seen : Set[E] = Set.empty
    var level = 0


    var res : Map[E,Int] = Map.empty

    while(!curLevel.isEmpty) {
      curLevel.foreach { n =>
        res += (n -> level)
        seen += n
        nextLevel ++= n.downstreamNodes.filterNot(seen.contains)
      }
      curLevel = nextLevel
      level += 1
      nextLevel = Set.empty
    }

    // circular references have no depths
    res ++= graph.filterNot(seen.contains).map( _ -> -1)

    res
  }

  def referenceChainsFor(node : E,dominators : Map[E,Option[E]]) : List[List[E]] = {
    val fifo = ListBuffer[List[E]]()
    var result : List[List[E]] = Nil
    var visited = Set[E](node)

    fifo += node :: Nil
    while (fifo.size > 0) {
      val chain: List[E] = fifo.remove(0)
      val curr: E =  chain.head
      // if the current node has a dominator we can chase it to the root
      dominators.get(curr) match {
        case Some(Some(d)) =>
          result = domChain(d,d :: chain,dominators) :: result
        case Some(None) =>
          result = chain :: result
        case None =>
          if(curr.upstreamNodes.isEmpty) // the chain is complete
            result = chain :: result
          else {
            val refSet = curr.upstreamNodes
            refSet foreach { t =>
              if (!visited.contains(t)) {
                visited += t
                fifo += t :: chain
              }
            }
          }
      }
    }
    result

  }

  @tailrec private final def domChain(node : E,chain : List[E],dominators : Map[E,Option[E]]) : List[E] = {
    dominators(node) match {
      case Some(d) => domChain(d,d :: chain,dominators)
      case None => chain
    }
  }
}
