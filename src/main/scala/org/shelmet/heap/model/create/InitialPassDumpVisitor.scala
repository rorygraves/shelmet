package org.shelmet.heap.model.create

import org.shelmet.heap.model._
import java.io.IOException
import scala.Some
import org.shelmet.heap.parser.{ClassFieldEntry, ClassStaticEntry}
import org.shelmet.heap.HeapId
import java.util.Date
import org.shelmet.heap.shared.{BaseFieldType, ClassType}

object InitialPassDumpVisitor {
  class ThreadObject(val threadId: Long,val  stackSeq: Int)
}

class InitialPassDumpVisitor(snapshot : Snapshot,callStack: Boolean) extends AbstractDumpVisitor(callStack) {

  import InitialPassDumpVisitor._

  var classCount = 0
  var objectCount = 0

  private var threadObjects = Map[Int, ThreadObject]()

  override def creationDate(date : Date) {
    snapshot.creationDate = Some(date)
  }

  override def identifierSize(size : Int) {
    snapshot.setIdentifierSize(size)
  }

  override def gcRootUnknown(id : Long) {
    snapshot.addRoot(new Root(snapshot,HeapId(id), None, UnknownRootType, ""))
  }

  override def gcRootThreadObj(id : Long,threadSeq : Int,stackSeq :Int) {
    threadObjects += (threadSeq -> new ThreadObject(id, stackSeq))
  }

  override def gcRootJNIGlobal(id : Long,jniGlobalRefId : Long) {
    snapshot.addRoot(new Root(snapshot,HeapId(id), None, NativeStaticRootType, ""))
  }

  def getThreadObjectFromSequence(threadSeq: Int): ThreadObject = {
    threadObjects.getOrElse(threadSeq,throw new IOException("Thread " + threadSeq + " not found for JNI local ref"))
  }

  override def gcRootJNILocal(heapRef : HeapId,threadSeq : Int,depth : Int) {
    val to = getThreadObjectFromSequence(threadSeq)
    val st = getStackTraceFromSerial(to.stackSeq).map(_.traceForDepth(depth + 1))
    snapshot.addRoot(new Root(snapshot,heapRef, Some(HeapId(to.threadId)), NativeLocalRootType, "", st))
  }

  override def gcRootJavaFrame(heapRef : HeapId,threadSeq : Int,depth : Int) {
    val to = getThreadObjectFromSequence(threadSeq)
    val st = getStackTraceFromSerial(to.stackSeq).map(_.traceForDepth(depth + 1))
    snapshot.addRoot(new Root(snapshot,heapRef, Some(HeapId(to.threadId)), JavaLocalRootType, "", st))
  }

  override def gcRootNativeStack(heapRef: HeapId,threadSeq : Int) {
    val to = getThreadObjectFromSequence(threadSeq)
    val st = getStackTraceFromSerial(to.stackSeq)
    snapshot.addRoot(new Root(snapshot,heapRef, Some(HeapId(to.threadId)), NativeStackRootType, "", st))
  }

  override def gcRootStickyClass(id : Long) {
    snapshot.addRoot(new Root(snapshot,HeapId(id), None, SystemClassRootType, ""))
  }

  override def gcRootThreadBlock(id : Long,threadSeq : Int) {
    val to = getThreadObjectFromSequence(threadSeq)
    val st = getStackTraceFromSerial(to.stackSeq)
    snapshot.addRoot(new Root(snapshot,HeapId(id), Some(HeapId(to.threadId)), ThreadBlockRootType, "", st))
  }

  override def gcRootMonitorUsed(id : Long) {
    snapshot.addRoot(new Root(snapshot,HeapId(id), None, BusyMonitorRootType, ""))
  }

  override def classDump(id : HeapId,stackTraceSerialId : Int,
                         superClassId : HeapId,
                         classLoaderId : HeapId,
                         signerId : HeapId,
                         protDomainId : HeapId,
                         instanceSize : Int,
                         constPoolEntries : Map[Int,Any],
                         staticItems : List[ClassStaticEntry],
                         fieldItems : List[ClassFieldEntry]) {
    classCount += 1
    val classSig = classNameFromObjectID.get(id) match {
      case Some(n) => n
      case None =>
        logger.warn("Class name not found for {}",id.toHex)
        "unknown-name@" + id.toHex
    }

    val className = ClassType.parse(classSig).toString

    val statics = staticItems.map { se =>
        val fieldName = getNameFromID(se.nameId)
        val f = new JavaField(fieldName,className + "." + fieldName,se.itemType)
        new JavaStatic(snapshot,f, se.value)
    }

    val stackTrace = getStackTraceFromSerial(stackTraceSerialId)

    val fields = fieldItems.map {
      fi =>
        val fieldName = getNameFromID(fi.nameId)
        new JavaField(fieldName,className + "." + fieldName,fi.itemType)
    }

    val c = new JavaClass(snapshot,id,className,superClassId,classLoaderId,signerId,protDomainId,statics,instanceSize,fields)
    snapshot.addClass(id, c)
    snapshot.setSiteTrace(c, stackTrace)
  }

  override def instanceDump(id : HeapId,stackTraceSerialId : Int,classId : HeapId,fields : Option[Vector[Any]],fieldsLengthBytes : Int) {
    objectCount +=1
  }

  override def objectArrayDump(id : HeapId,stackTraceSerialId : Int,numElements : Int,classId : HeapId,elementIDs : Seq[HeapId]) {
    objectCount += 1
  }

  override def primitiveArray(heapId : HeapId,stackTraceSerialID : Int,fieldType : BaseFieldType,data : Seq[AnyVal]) {
    objectCount += 1
  }


}
