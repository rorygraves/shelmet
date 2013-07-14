package org.shelmet.heap.model

object ReachableObjects {

  def find(root: JavaHeapObject) = {
    var bag =  Set[JavaHeapObject]()

    def visitFunc(t : JavaHeapObject) {
      if (t != null && t.size > 0 && !bag.contains(t)) {
        bag += t
        t.visitReferencedObjects(visitFunc)
      }
    }

    visitFunc(root)
    bag -= root

    import org.shelmet.heap.util.SortUtil.sortByFirstThen
    val reachables = bag.toList.sortWith(sortByFirstThen(
      (l,r)=> r.size - l.size,
      (l,r) => l.toString.compareTo(r.toString)))

    var totalSize = root.size
    for (thing <- reachables) totalSize += thing.size

    new ReachableObjects(root, reachables,totalSize)
  }
}

case class ReachableObjects(root: JavaHeapObject,reachables: List[JavaHeapObject],totalSize: Long)