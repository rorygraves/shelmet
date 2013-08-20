package org.shelmet.heap.model

import org.shelmet.heap.HeapId

/**
 * Represents a member of the rootset, that is, one of the objects that
 * the GC starts from when marking reachable objects.
 */
object Root {
  // TODO Make this an enum or equivalent
  final val INVALID_TYPE: Int = 0
  final val UNKNOWN: Int = 1
  final val SYSTEM_CLASS: Int = 2
  final val NATIVE_LOCAL: Int = 3
  final val NATIVE_STATIC: Int = 4
  final val THREAD_BLOCK: Int = 5
  final val BUSY_MONITOR: Int = 6
  final val JAVA_LOCAL: Int = 7
  final val NATIVE_STACK: Int = 8
  final val JAVA_STATIC: Int = 9
}

class Root(snapshot : Snapshot,val valueHeapId: HeapId, refererId: HeapId, val rootType: Int, description: String,
           val stackTrace: Option[StackTrace]= None) {

  /** The unique index of this root */
  var index: Int = -1

  implicit val ss : Snapshot = snapshot

  def getReferencedItem = valueHeapId.getOpt

  /**
   * Get the object that's responsible for this root, if there is one.
   * This will be None, Some(Thread), or Some(Class)
   */
  def referer: Option[JavaHeapObject] = refererId.getOpt

  def getDescription: String = {
    if ("" == description)
      getTypeName + " Reference"
    else
      description
  }

  override def toString = getDescription + refererId.getOpt.map(f => " from " + f.toString).getOrElse("")
  //+ stackTrace.map( s => s.toString).getOrElse("")

  /**
   * Return type.  We guarantee that more interesting roots will have
   * a type that is numerically higher.
   */
  def getType: Int = {
    rootType
  }

  def getTypeName: String = {
    import Root._
    rootType match {
      case INVALID_TYPE =>
        "Invalid (?!?)"
      case UNKNOWN =>
        "Unknown"
      case SYSTEM_CLASS =>
        "System Class"
      case NATIVE_LOCAL =>
        "JNI Local"
      case NATIVE_STATIC =>
        "JNI Global"
      case THREAD_BLOCK =>
        "Thread Block"
      case BUSY_MONITOR =>
        "Busy Monitor"
      case JAVA_LOCAL =>
        "Java Local"
      case NATIVE_STACK =>
        "Native Stack (possibly Java local)"
      case JAVA_STATIC =>
        "Java Static"
      case _ =>
        "??"
    }
  }

  /**
   * Given two Root instances, return the one that is most interesting.
   */
  def mostInteresting(other: Root): Root = {
    if (other.rootType > this.rootType)
      other
    else
      this
  }
}