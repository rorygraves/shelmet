package org.shelmet.heap.model

import org.shelmet.heap.HeapId

sealed abstract class RootType(val sortOrder : Int,val name : String)
case object UnknownRootType extends RootType(1,"Unknown")
case object SystemClassRootType extends RootType(2,"System Class")
case object NativeLocalRootType extends RootType(3,"JNI Local")
case object NativeStaticRootType extends RootType(4,"JNI Global")
case object ThreadBlockRootType extends RootType(5,"Thread Block")
case object BusyMonitorRootType extends RootType(6,"Busy Monitor")
case object JavaLocalRootType extends RootType(7,"Java Local")
case object NativeStackRootType extends RootType(8,"Native Stack (possibly Java local)")
case object JavaStaticRootType extends RootType(9,"Java Static")

/**
 * Represents a member of the rootset, that is, one of the objects that
 * the GC starts from when marking reachable objects.
 */
class Root(snapshot : Snapshot,val valueHeapId: HeapId, refererId: Option[HeapId], val rootType: RootType, description: String,
           val stackTrace: Option[StackTrace]= None) {

  /** The unique index of this root */
  var index: Int = -1

  implicit val ss : Snapshot = snapshot

  def getReferencedItem = valueHeapId.getOpt

  /**
   * Get the object that's responsible for this root, if there is one.
   * This will be None, Some(Thread), or Some(Class)
   */
  def referer: Option[JavaHeapObject] = refererId.map(_.getOpt).getOrElse(None)

  def getDescription: String = {
    if ("" == description)
      getTypeName + " Reference"
    else
      description
  }

  override def toString = getDescription + referer.map(f => " from " + f.toString).getOrElse("")

  def getTypeName: String = rootType.name
}