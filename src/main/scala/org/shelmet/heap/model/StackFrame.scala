package org.shelmet.heap.model

/**
 * Represents a stack frame.
 */
object StackFrame {
  val LINE_NUMBER_UNKNOWN: Int = -1
  val LINE_NUMBER_COMPILED: Int = -2
  val LINE_NUMBER_NATIVE: Int = -3
}

class StackFrame(val methodName: String, val methodSignature: String, val className: String, val sourceFileName: String, lineNumber: Int) {

  def getLineNumber: String = {
    import StackFrame._
    lineNumber match {
      case LINE_NUMBER_UNKNOWN => "(unknown)"
      case LINE_NUMBER_COMPILED => "(compiled method)"
      case LINE_NUMBER_NATIVE => "(native method)"
      case _ => Integer.toString(lineNumber, 10)
    }
  }
}