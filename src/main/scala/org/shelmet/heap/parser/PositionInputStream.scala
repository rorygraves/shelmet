package org.shelmet.heap.parser

import java.io.FilterInputStream
import java.io.InputStream

/**
 * InputStream that keeps track of total bytes read (in effect
 * 'position' in stream) from the input stream.
 *
 */
class PositionInputStream(in: InputStream) extends FilterInputStream(in) {

  var position: Long = 0L

  override def read: Int = {
    val res: Int = super.read
    if (res != -1)
      position += 1
    res
  }

  override def read(b: Array[Byte], off: Int, len: Int): Int = {
    val res: Int = super.read(b, off, len)
    if (res != -1) position += res
    res
  }

  override def skip(n: Long): Long = {
    val res: Long = super.skip(n)
    position += res
    res
  }

  override def markSupported: Boolean = false
  override def mark(readLimit: Int) {

    throw new UnsupportedOperationException("mark")
  }

  override def reset() {
    throw new UnsupportedOperationException("reset")
  }
}