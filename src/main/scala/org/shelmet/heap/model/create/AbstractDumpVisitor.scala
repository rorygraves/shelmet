package org.shelmet.heap.model.create

import org.shelmet.heap.parser.{ClassFieldEntry, ClassStaticEntry, DumpVisitor}
import org.shelmet.heap.util.Misc._
import scala.Some
import java.io.IOException
import org.shelmet.heap.model.{JavaField, JavaStatic, StackFrame, StackTrace}
import org.shelmet.heap.HeapId
import com.typesafe.scalalogging.slf4j.Logging
import org.shelmet.heap.shared.{ClassType, InstanceId}

class AbstractDumpVisitor(callStack: Boolean) extends DumpVisitor with Logging {

  var names = Map[Long, String]()
  var classNameFromObjectID = Map[HeapId, String]()
  var nextObjectIdForClass = Map[HeapId,Int]()
  var classNameFromSerialNo = Map[Int, String]()

  def getInstanceId(objectTypeId : HeapId) : InstanceId = {
    val res = nextObjectIdForClass.getOrElse(objectTypeId,1)
    nextObjectIdForClass += (objectTypeId -> (res + 1))
    InstanceId(objectTypeId,res)
  }


  // the heap indexed items in the map
  var stackTraces = Map[Int, StackTrace]()
  var stackFrames = Map[Long, StackFrame]()

  final override def utf8String(id : Long,string : String) {
    names += (id -> string)
  }

  final def getNameFromID(id: Long): String = {
    if (id == 0L)
      return ""

    names.get(id) match {
      case Some(result) => result
      case None =>
        logger.info("Name not found at {}",toHex(id))
        "unresolved name " + toHex(id)
    }
  }

  final override def loadClass(classSerialNo : Int,classID : HeapId,stackTraceSerialNo : Int,classNameID : Long) {
    val className = getNameFromID(classNameID).replace('/', '.')

    classNameFromObjectID += (classID ->  className)
    classNameFromSerialNo += (classSerialNo -> className)
  }

  final override def stackTrace(serialNo : Int,threadSerialNo : Int,frameIDs : Vector[Long]) {

    if(callStack) {
      val frames =  frameIDs.map { fid =>
        stackFrames.getOrElse(fid,throw new IOException(s"Stack frame ${toHex(fid)} not found"))
      }

      stackTraces += (serialNo -> new StackTrace(frames.toVector))
    }
  }

  final def getStackTraceFromSerial(ser: Int): Option[StackTrace] = {
    if(!callStack)
      return None

    val result = stackTraces.get(ser)
    if (result == None)
      logger.warn(s"Stack trace not found for serial # $ser")

    result
  }

  final override def stackFrame(id : Long,methodNameId : Long,methodSigId : Long,sourceFileNameId : Long,classSerialId : Int,lineNumber : Int) {
    if (callStack) {
      val methodName = getNameFromID(methodNameId)
      val methodSig = getNameFromID(methodSigId)
      val sourceFile = getNameFromID(sourceFileNameId)
      val className = classNameFromSerialNo.getOrElse(classSerialId,null)

      val adjLineNo = if (lineNumber < StackFrame.LINE_NUMBER_NATIVE) {
        logger.warn(s"Weird stack frame line number:  $lineNumber")
        StackFrame.LINE_NUMBER_UNKNOWN
      } else lineNumber

      stackFrames += (id -> new StackFrame(methodName, methodSig, className, sourceFile, adjLineNo))
    }
  }

  def classNameFromObjectId(id : HeapId) : String = {
    val classSig = classNameFromObjectID.get(id) match {
      case Some(n) => n
      case None =>
        logger.warn("Class name not found for {}",id.toHex)
        s"unknown-name@${id.toHex}"
    }

    ClassType.parse(classSig).toString
  }

  def readStatics(className : String,staticItems : List[ClassStaticEntry]) : List[JavaStatic] = {
    staticItems.map { se =>
      val fieldName = getNameFromID(se.nameId)
      val f = new JavaField(fieldName,className + "." + fieldName,se.itemType)
      new JavaStatic(f, se.value)
    }
  }

  def readFields(className : String,fieldItems : List[ClassFieldEntry]) : List[JavaField] = {
    fieldItems.map {
      fi =>
        val fieldName = getNameFromID(fi.nameId)
        new JavaField(fieldName,className + "." + fieldName,fi.itemType)
    }
  }
}
