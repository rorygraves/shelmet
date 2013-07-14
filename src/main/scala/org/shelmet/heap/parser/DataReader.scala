package org.shelmet.heap.parser

import org.shelmet.heap.model.Snapshot
import org.shelmet.heap.HeapId

class DataReader(in: PositionDataInputStream,val identifierSize : Int) {
  def readID: Long = {
    if (identifierSize == 8)
      in.readLong
    else
      Snapshot.SMALL_ID_MASK & in.readInt.asInstanceOf[Long]
  }

  def position = in.position

  def readHeapId : HeapId = HeapId(readID)

  def skipBytes(length: Long) {
    in.skipBytes(length.asInstanceOf[Int])
  }

  def readBoundedString(length : Int) : String = {
    val chars = new Array[Byte](length)
    in.readFully(chars)
    new String(chars)
  }

  def readUnsignedByte: Int = in.readUnsignedByte()
  def readInt : Int = in.readInt()
  def readLong : Long = in.readLong()
  def readFloat : Float = in.readFloat()
  def readDouble : Double = in.readDouble()
  def readUnsignedShort : Int = in.readUnsignedShort()
  def readByte : Byte = in.readByte()
  def readChar : Char = in.readChar()
  def readShort : Short = in.readShort()
  def readBoolean : Boolean = in.readBoolean()

  def readBytes(noBytes : Int) : Array[Byte] = {
    val bytes = new Array[Byte](noBytes)
    in.readFully(bytes)
    bytes
  }
}
