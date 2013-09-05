package org.shelmet.heap.model

import org.shelmet.heap.util.Misc
import org.shelmet.heap.HeapId
import scala.collection.SortedSet

/**
 * Represents an object that's allocated out of the Java heap.  It occupies
 * memory in the VM.  It can be a
 * JavaClass, a JavaObjectArray, a JavaValueArray or a JavaObject.
 */
abstract class JavaHeapObject(val heapId : HeapId,snapshotV : Snapshot) extends Ordered[JavaHeapObject] {

  implicit val snapshot : Snapshot = snapshotV

  var referersSet: SortedSet[HeapId] = SortedSet.empty

  var retainedCalculated = false
  var retaining : Long = 0

  var dominator : Dominator = UnknownDominator

  def retainedSize = size + retaining
  var minDepthToRoot = -1
  var maxDepthToRoot = -1

  def addDepth(depth : Int) {
    if(minDepthToRoot == -1 || depth < minDepthToRoot)
      minDepthToRoot = depth

    if(maxDepthToRoot == -1 || depth > maxDepthToRoot)
      maxDepthToRoot = depth
  }

  override def compare(that: JavaHeapObject): Int = heapId.compareTo(that.heapId)

  def referers : SortedSet[JavaHeapObject] = referersSet.map(snapshot.findHeapObject(_).get)

  def noRefers = referersSet.size

  private[model] def addReferenceFrom(other: JavaHeapObject) {
    referersSet += other.heapId
  }

  def getClazz: JavaClass

  override def equals(other : Any) = other match {
    case j : JavaHeapObject => j.heapId == this.heapId
    case _ => false
  }

  /**
   * Do any initialization this thing needs after its data is read in.
   */
  def resolve(snapshot: Snapshot)

  /**
   * @return the id of this thing as hex string
   */
  def getIdString: String = Misc.toHex(heapId.id)

  override def toString: String = getClazz.displayName + "@" + getIdString

  override val hashCode : Int = heapId.hashCode()

  /**
   * @return the StackTrace of the point of allocation of this object,
   *         or null if unknown
   */
  def getAllocatedFrom: Option[StackTrace] = snapshot.getSiteTrace(this)

  /**
   * Tell the visitor about all of the objects we refer to
   */
  def visitReferencedObjects(visit : JavaHeapObject => Unit,includeStatics : Boolean = true) {
    visit(getClazz)
  }

  private[model] def addReferenceFromRoot(r: Root) {
    snapshot.addReferenceFromRoot(r, this)
  }

  /**
   * Return the set of root references to this object.
   */
  def getRootReferences: Set[Root] = snapshot.getRoots(this)

  /**
   * Given other, which the caller promises is in referers, determines if
   * the reference is only a weak reference.
   */
  def refersOnlyWeaklyTo(other: JavaHeapObject): Boolean = false

  /**
   * Describe the reference that this thing has to target.  This will only
   * be called if target is in the array returned by getChildrenForRootset.
   */
  def describeReferenceTo(target: JavaHeapObject): List[String] =
    if(getClazz == target)
      List("instance")
    else
      List.empty

  /**
   * @return the size of this object, in bytes, including VM overhead
   */
  def size: Int
}