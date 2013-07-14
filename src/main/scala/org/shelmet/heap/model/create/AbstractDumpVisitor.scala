package org.shelmet.heap.model.create

import org.shelmet.heap.parser.DumpVisitor
import org.shelmet.heap.util.Misc._
import scala.Some
import java.io.IOException
import org.shelmet.heap.model.{StackFrame, StackTrace}
import org.shelmet.heap.HeapId
import com.typesafe.scalalogging.slf4j.Logging

class AbstractDumpVisitor(callStack: Boolean) extends DumpVisitor with Logging {

  var names = Map[Long, String]()
  var classNameFromObjectID = Map[HeapId, String]()
  var classNameFromSerialNo = Map[Int, String]()

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
        stackFrames.getOrElse(fid,throw new IOException("Stack frame " + toHex(fid) + " not found"))
      }

      stackTraces += (serialNo -> new StackTrace(frames.toVector))
    }
  }

  final def getStackTraceFromSerial(ser: Int): Option[StackTrace] = {
    if(!callStack)
      return None

    val result = stackTraces.get(ser)
    if (result == None)
      logger.warn(s"Stack trace not found for serial # ${ser}")

    result
  }

  final override def stackFrame(id : Long,methodNameId : Long,methodSigId : Long,sourceFileNameId : Long,classSerialId : Int,lineNumber : Int) {
    if (callStack) {
      val methodName = getNameFromID(methodNameId)
      val methodSig = getNameFromID(methodSigId)
      val sourceFile = getNameFromID(sourceFileNameId)
      val className = classNameFromSerialNo.getOrElse(classSerialId,null)

      val adjLineNo = if (lineNumber < StackFrame.LINE_NUMBER_NATIVE) {
        logger.warn(s"Weird stack frame line number:  ${lineNumber}")
        StackFrame.LINE_NUMBER_UNKNOWN
      } else lineNumber

      stackFrames += (id -> new StackFrame(methodName, methodSig, className, sourceFile, adjLineNo))
    }
  }
}
