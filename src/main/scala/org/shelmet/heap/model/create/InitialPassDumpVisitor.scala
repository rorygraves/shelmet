package org.shelmet.heap.model.create

import org.shelmet.heap.model._
import java.io.IOException
import scala.Some
import org.shelmet.heap.parser.{ClassFieldEntry, ClassStaticEntry}
import org.shelmet.heap.HeapId
import java.util.Date

object InitialPassDumpVisitor {
  class ThreadObject(val threadId: Long,val  stackSeq: Int)
}

class InitialPassDumpVisitor(snapshot : Snapshot,callStack: Boolean) extends AbstractDumpVisitor(callStack) {

  import InitialPassDumpVisitor._

  private var threadObjects = Map[Int, ThreadObject]()

  override def creationDate(date : Date) {
    snapshot.creationDate = Some(date)
  }

  override def identifierSize(size : Int) {
    snapshot.setIdentifierSize(size)
  }

  override def gcRootUnknown(id : Long) {
    snapshot.addRoot(new Root(snapshot,HeapId(id), HeapId(0), Root.UNKNOWN, ""))
  }

  override def gcRootThreadObj(id : Long,threadSeq : Int,stackSeq :Int) {
    threadObjects += (threadSeq -> new ThreadObject(id, stackSeq))
  }

  override def gcRootJNIGlobal(id : Long,jniGlobalRefId : Long) {
    snapshot.addRoot(new Root(snapshot,HeapId(id), HeapId(0), Root.NATIVE_STATIC, ""))
  }

  def getThreadObjectFromSequence(threadSeq: Int): ThreadObject = {
    threadObjects.getOrElse(threadSeq,throw new IOException("Thread " + threadSeq + " not found for JNI local ref"))
  }

  override def gcRootJNILocal(heapRef : HeapId,threadSeq : Int,depth : Int) {
    val to = getThreadObjectFromSequence(threadSeq)
    val st = getStackTraceFromSerial(to.stackSeq).map(_.traceForDepth(depth + 1))
    snapshot.addRoot(new Root(snapshot,heapRef, HeapId(to.threadId), Root.NATIVE_LOCAL, "", st))
  }

  override def gcRootJavaFrame(heapRef : HeapId,threadSeq : Int,depth : Int) {
    val to = getThreadObjectFromSequence(threadSeq)
    val st = getStackTraceFromSerial(to.stackSeq).map(_.traceForDepth(depth + 1))
    snapshot.addRoot(new Root(snapshot,heapRef, HeapId(to.threadId), Root.JAVA_LOCAL, "", st))
  }

  override def gcRootNativeStack(heapRef: HeapId,threadSeq : Int) {
    val to = getThreadObjectFromSequence(threadSeq)
    val st = getStackTraceFromSerial(to.stackSeq)
    snapshot.addRoot(new Root(snapshot,heapRef, HeapId(to.threadId), Root.NATIVE_STACK, "", st))
  }

  override def gcRootStickyClass(id : Long) {
    snapshot.addRoot(new Root(snapshot,HeapId(id), HeapId(0), Root.SYSTEM_CLASS, ""))
  }

  override def gcRootThreadBlock(id : Long,threadSeq : Int) {
    val to = getThreadObjectFromSequence(threadSeq)
    val st = getStackTraceFromSerial(to.stackSeq)
    snapshot.addRoot(new Root(snapshot,HeapId(id), HeapId(to.threadId), Root.THREAD_BLOCK, "", st))
  }

  override def gcRootMonitorUsed(id : Long) {
    snapshot.addRoot(new Root(snapshot,HeapId(id), HeapId(0), Root.BUSY_MONITOR, ""))
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

    val name = classNameFromObjectID.get(id) match {
      case Some(n) => n
      case None =>
        logger.warn("Class name not found for {}",id.toHex)
        "unknown-name@" + id.toHex
    }

    val statics = staticItems.map { se =>
        val fieldName = getNameFromID(se.nameId)
        val signature = se.itemType.toString
        val f = new JavaField(fieldName,name + "." + fieldName, signature)
        new JavaStatic(snapshot,f, se.value)
    }

    val stackTrace = getStackTraceFromSerial(stackTraceSerialId)

    val fields = fieldItems.map {
      fi =>
        val fieldName = getNameFromID(fi.nameId)
        val sig = fi.itemType.toString
        new JavaField(fieldName,name + "." + fieldName,sig)
    }

    val c = new JavaClass(snapshot,id,name,superClassId,classLoaderId,signerId,protDomainId,statics,instanceSize,fields)
    snapshot.addClass(id, c)
    snapshot.setSiteTrace(c, stackTrace)
  }
}
