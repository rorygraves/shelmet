package org.shelmet.heap.parser

import org.shelmet.heap.shared._

object ArrayWrapper {
  def readBooleanArray(in : DataReader, length : Int) : BooleanArrayWrapper = {
    val arr = new Array[Boolean](length)
    for(i <- 0 until length)
      arr(i) = in.readBoolean
    new BooleanArrayWrapper(arr)
  }

  def readByteArray(in : DataReader, length : Int) : ByteArrayWrapper = {
    val arr = new Array[Byte](length)
    for(i <- 0 until length)
      arr(i) = in.readByte
    new ByteArrayWrapper(arr)
  }

  def readCharArray(in : DataReader, length : Int) : CharArrayWrapper = {
    val arr = new Array[Char](length)
    for(i <- 0 until length)
      arr(i) = in.readChar
    new CharArrayWrapper(arr)
  }

  def readShortArray(in : DataReader, length : Int) : ShortArrayWrapper = {
    val arr = new Array[Short](length)
    for(i <- 0 until length)
      arr(i) = in.readShort
    new ShortArrayWrapper(arr)
  }

  def readIntArray(in : DataReader, length : Int) : IntArrayWrapper = {
    val arr = new Array[Int](length)
    for(i <- 0 until length)
      arr(i) = in.readInt
    new IntArrayWrapper(arr)
  }

  def readLongArray(in : DataReader, length : Int) : LongArrayWrapper = {
    val arr = new Array[Long](length)
    for(i <- 0 until length)
      arr(i) = in.readLong
    new LongArrayWrapper(arr)
  }

  def readFloatArray(in : DataReader, length : Int) : FloatArrayWrapper = {
    val arr = new Array[Float](length)
    for(i <- 0 until length)
      arr(i) = in.readFloat
    new FloatArrayWrapper(arr)
  }

  def readDoubleArray(in : DataReader, length : Int) : DoubleArrayWrapper = {
    val arr = new Array[Double](length)
    for(i <- 0 until length)
      arr(i) = in.readDouble
    new DoubleArrayWrapper(arr)
  }

}

/** A defensive wrapper around an underlying primitive array read from the dump */

sealed trait ArrayWrapper extends Serializable {
  def arrayType : BaseFieldType
  def size : Int
  def asSeq : Seq[AnyVal]
}

class BooleanArrayWrapper(data : Array[Boolean]) extends ArrayWrapper {
  override def arrayType = BooleanFieldType
  def asSeq = data.toSeq
  def size = data.size
}


class ByteArrayWrapper(data : Array[Byte]) extends ArrayWrapper {
  override def arrayType = ByteFieldType
  def asSeq = data.toSeq
  def size = data.size
}

class CharArrayWrapper(data : Array[Char]) extends ArrayWrapper {
  override def arrayType = CharFieldType
  def asSeq = data.toSeq
  def size = data.size
  override def toString = new String(data)
}

class ShortArrayWrapper(data : Array[Short]) extends ArrayWrapper {
  override def arrayType = ShortFieldType
  def asSeq = data.toSeq
  def size = data.size
}

class IntArrayWrapper(data : Array[Int]) extends ArrayWrapper {
  override def arrayType = IntFieldType
  def asSeq = data.toSeq
  def size = data.size
}

class LongArrayWrapper(data : Array[Long]) extends ArrayWrapper {
  override def arrayType = LongFieldType
  def asSeq = data.toSeq
  def size = data.size
}

class FloatArrayWrapper(data : Array[Float]) extends ArrayWrapper {
  override def arrayType = FloatFieldType
  def asSeq = data.toSeq
  def size = data.size
}

class DoubleArrayWrapper(data : Array[Double]) extends ArrayWrapper {
  override def arrayType = DoubleFieldType
  def asSeq = data.toSeq
  def size = data.size
}
