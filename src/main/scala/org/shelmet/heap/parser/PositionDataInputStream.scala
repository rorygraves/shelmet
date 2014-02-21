package org.shelmet.heap.parser

import java.io.{FilterInputStream, DataInputStream, InputStream}
import PositionDataInputStream.PositionInputStream


object PositionDataInputStream {

  // we can skip implementing the checks for mark/reset as this class is purely internal to PositionDataInputStream
  private class PositionInputStream(in: InputStream) extends FilterInputStream(in) {

    var position: Long = 0L

    override def read: Int = {
      val res = super.read
      if (res != -1)
        position += 1
      res
    }

    override def read(b: Array[Byte], off: Int, len: Int): Int = {
      val res = super.read(b, off, len)
      if (res != -1) position += res
      res
    }

    override def skip(n: Long): Long = {
      val res = super.skip(n)
      position += res
      res
    }
  }

  def apply(inRef: InputStream) = new PositionDataInputStream(new PositionInputStream(inRef))
}

/**
 * A DataInputStream that keeps track of total bytes read
 * (in effect 'position' in stream) so far.
 * We use an internal support class (PositionInputStream) because DataInputStream finalises read methods.
 */
class PositionDataInputStream private (inRef: PositionDataInputStream.PositionInputStream) extends DataInputStream(inRef) {

  override def markSupported: Boolean = false

  override def mark(readLimit: Int) {
    throw new UnsupportedOperationException("mark")
  }

  override def reset() {
    throw new UnsupportedOperationException("reset")
  }

  def readUnsignedInt: Long = 0x0FFFFFFFFL & readInt

  def position: Long = inRef.position
}