package org.shelmet.heap.parser

import java.io.DataInputStream
import java.io.InputStream

/**
 * A DataInputStream that keeps track of total bytes read
 * (in effect 'position' in stream) so far.
 */
class PositionDataInputStream(inRef: InputStream)
  extends DataInputStream(if (inRef.isInstanceOf[PositionInputStream]) inRef else new PositionInputStream(inRef)) {

  override def markSupported: Boolean = false

  override def mark(readLimit: Int) {
    throw new UnsupportedOperationException("mark")
  }

  override def reset() {
    throw new UnsupportedOperationException("reset")
  }

  def position: Long = {
    in.asInstanceOf[PositionInputStream].position
  }
}