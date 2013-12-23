package org.shelmet.heap.model

import org.shelmet.heap.util.Misc
import org.shelmet.heap.HeapId
import org.shelmet.heap.shared.InstanceId
import scala.collection.SortedSet

/**
 * Represents an object that's allocated out of the Java heap.  It occupies
 * memory in the VM.  It can be a
 * JavaClass, a JavaObjectArray, a JavaValueArray or a JavaObject.
 */
abstract class JavaHeapObject(val heapId : HeapId,val objIdent : Option[InstanceId],snapshotV : Snapshot) extends Ordered[JavaHeapObject] {

  var hardRefersSet : SortedSet[HeapId] = SortedSet.empty
  var softRefersSet : SortedSet[HeapId] = SortedSet.empty

  def referersSet : SortedSet[HeapId] =
    if(softRefersSet.isEmpty)
      hardRefersSet
    else if(hardRefersSet.isEmpty)
      softRefersSet
    else
      softRefersSet ++ hardRefersSet


  var retainedCalculated = false
  var retaining : Long = 0

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

  def referers : SortedSet[JavaHeapObject] = referersSet.map(Snapshot.instance.findHeapObject(_).get)

  def noRefers = softRefersSet.size + hardRefersSet.size

  private[model] def addReferenceFrom(other: JavaHeapObject) {
    if(other.refersOnlyWeaklyTo(this))
      softRefersSet += other.heapId
    else
      hardRefersSet += other.heapId
  }

  def getClazz: JavaClass

  override def equals(other : Any) = other match {
    case j : JavaHeapObject => (j eq this) || j.heapId == this.heapId
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

  override def toString: String = {
    getClazz.displayName + (objIdent match {
      case Some(ident) => " #" + ident.id
      case None => "@"  + getIdString
    })
  }

  override val hashCode : Int = heapId.hashCode()

  /**
   * @return the StackTrace of the point of allocation of this object,
   *         or null if unknown
   */
  def getAllocatedFrom: Option[StackTrace] = Snapshot.instance.getSiteTrace(this)

  /**
   * Tell the visitor about all of the objects we refer to
   */
  def visitReferencedObjects(visit : JavaHeapObject => Unit,includeStatics : Boolean = true) {
    visit(getClazz)
  }

  private[model] def addReferenceFromRoot(r: Root) {
    Snapshot.instance.addReferenceFromRoot(r, this)
  }

  /**
   * Return the set of root references to this object.
   */
  def getRootReferences: Set[Root] = Snapshot.instance.getRoots(this)

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
   * @return the size of this object, in bytes, including VM overhead
   */
  def size: Int
}