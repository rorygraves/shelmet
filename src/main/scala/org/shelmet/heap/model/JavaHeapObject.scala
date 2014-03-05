package org.shelmet.heap.model

import org.shelmet.heap.util.Misc
import org.shelmet.heap.HeapId

/**
 * Represents an object that's allocated out of the Java heap.  It occupies
 * memory in the VM.  It can be a
 * JavaClass, a JavaObjectArray, a JavaValueArray or a JavaObject.
 */
abstract class JavaHeapObject(val heapId : HeapId,snapshotV : Snapshot) extends Ordered[JavaHeapObject] {

  override def compare(that: JavaHeapObject): Int = heapId.compareTo(that.heapId)

  def getClazz: JavaClass

  override def equals(other : Any) = other match {
    case j : JavaHeapObject => (j eq this) || j.heapId == this.heapId
    case _ => false
  }

  /**
   * Do any initialization this thing needs after its data is read in.
   */
  def resolve(snapshot: Snapshot) {}

  /**
   * @return the id of this thing as hex string
   */
  def getIdString: String = Misc.toHex(heapId.id)

  override def toString: String = getClazz.displayName +  "@"  + getIdString

  override val hashCode : Int = heapId.hashCode()

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
}