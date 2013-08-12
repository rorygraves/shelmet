package org.shelmet.heap.parser

import org.shelmet.heap.HeapId
import java.util.Date

trait DumpVisitor {
  def creationDate(date : Date) {}
  def identifierSize(size : Int) {}
  def utf8String(id : Long,string : String) {}
  def loadClass(classSerialNo : Int,classID : HeapId,stackTraceSerialNo : Int,classNameId : Long) {}
  def gcRootUnknown(id : Long) {}
  def gcRootThreadObj(id : Long,threadSeq : Int,stackSeq :Int) {}
  def gcRootJNIGlobal(id : Long,jniGlobalRefId : Long) {}
  def gcRootJNILocal(heapRef : HeapId,threadSeq : Int,depth : Int) {}
  def gcRootJavaFrame(heapRef : HeapId,threadSeq : Int,depth : Int) {}
  def gcRootNativeStack(id: HeapId,threadSeq : Int) {}
  def gcRootStickyClass(id : Long) {}
  def gcRootThreadBlock(id : Long,threadSeq : Int) {}
  def gcRootMonitorUsed(id : Long) {}

  /** Given a heap id of a class object return the signatures for that object
    * @return a list of field signatures
    */
  def getClassFieldInfo(classHeapId : HeapId) : Option[List[String]]  = { None }

  def classDump(id : HeapId,stackTraceSerialId : Int,
                superClassId : HeapId,
                classLoaderId : HeapId,
                signerId : HeapId,
                protDomainId : HeapId,
                instanceSize : Int,
                constPoolEntries : Map[Int,Any],
                staticItems : List[ClassStaticEntry],
                fieldItems : List[ClassFieldEntry]) {}
  def instanceDump(id : HeapId,stackTraceSerialId : Int,classId : HeapId,fields : Option[Vector[Any]],fieldsLengthBytes : Int) {}
  def objectArrayDump(id : HeapId,stackTraceSerialId : Int,numElements : Int,elementClassId : HeapId,elementIDs : Seq[HeapId]) {}
  def unloadClass(classSerialNo : Int) {}
  def heapDumpEnd() {}
  def heapSummary(totalLiveBytes : Int,totalLiveInstances : Int,totalBytesAllocated : Long,totalInstancesAllocated : Long) {}
  def stackFrame(id : Long,methodNameId : Long,methodSigId : Long,sourceFileNameId : Long,classSerialId : Int,lineNo : Int) {}
  def stackTrace(serialNo : Int,threadSerialNo : Int,frameIDs : Vector[Long]) {}
  def primitiveArray(id : HeapId,stackTraceSerialID : Int,primitiveSignature : Byte,elementSize : Int,data : AnyRef) {}
}