package org.shelmet.heap.model

/**
 * Represents a stack trace, that is, an ordered collection of stack frames.
 */
class StackTrace(val frames: Vector[StackFrame]) {
  /**
   * @param depth  The minimum reasonable depth is 1.
   * @return a (possibly new) StackTrace that is limited to depth.
   */
  def traceForDepth(depth: Int): StackTrace = {
    if (depth >= frames.length)
      this
    else
      new StackTrace(frames.take(depth))
  }
}