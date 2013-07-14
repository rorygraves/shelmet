package org.shelmet.heap.parser

import java.io.ByteArrayInputStream

class BlockDataReader(data : Array[Byte],identifierSize : Int) {

  def getReader : DataReader = new DataReader(new PositionDataInputStream(new ByteArrayInputStream(data)),identifierSize)
  def length = data.length
}
