package org.shelmet.heap.model

import org.shelmet.heap.util.Misc
import org.shelmet.heap.HeapId

/**
 * Represents an object that's allocated out of the Java heap.  It occupies
 * memory in the VM.  It can be a
 * JavaClass, a JavaObjectArray, a JavaValueArray or a JavaObject.
 */
abstract class JavaHeapObject(val heapId : HeapId,snapshotV : Snapshot) {

  implicit val snapshot : Snapshot = snapshotV

  private var referersSet: Set[HeapId] = Set()

  def referers : Set[JavaHeapObject] = referersSet.map(snapshot.findHeapObject(_).get)

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
  def visitReferencedObjects(visit : JavaHeapObject => Unit) {
    visit(getClazz)
  }

  private[model] def addReferenceFromRoot(r: Root) {
    snapshot.addReferenceFromRoot(r, this)
  }

  /**
   * If the rootset includes this object, return a Root describing one
   * of the reasons why.
   */
  def getRoot: Root = snapshot.getRoot(this)

  /**
   * Given other, which the caller promises is in referers, determines if
   * the reference is only a weak reference.
   */
  def refersOnlyWeaklyTo(ss: Snapshot, other: JavaHeapObject): Boolean = false

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