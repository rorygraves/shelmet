package org.shelmet.heap.parser

import akka.event.slf4j.SLF4JLogging
import org.shelmet.heap.util.Misc
import java.io._
import ArrayTypeCodes._
import scala.collection.mutable.ListBuffer
import java.util.Date
import org.shelmet.heap.shared._
import org.shelmet.heap.HeapId

/**
 * Object that's used to read a hprof file.
 */
object HprofReader extends SLF4JLogging {
  val MAGIC_NUMBER: Int = 0x4a415641

  private val VERSION_1_0_1 = "PROFILE 1.0.1"
  private val VERSION_1_0_2 = "PROFILE 1.0.2"

  private val HPROF_UTF8: Int = 0x01
  private val HPROF_LOAD_CLASS: Int = 0x02
  private val HPROF_UNLOAD_CLASS: Int = 0x03
  private val HPROF_STACK_FRAME: Int = 0x04
  private val HPROF_STACK_TRACE: Int = 0x05
  private val HPROF_ALLOC_SITES: Int = 0x06
  private val HPROF_HEAP_SUMMARY: Int = 0x07
  private val HPROF_START_THREAD: Int = 0x0a
  private val HPROF_END_THREAD: Int = 0x0b
  private val HPROF_HEAP_DUMP: Int = 0x0c
  private val HPROF_CPU_SAMPLES: Int = 0x0d
  private val HPROF_CONTROL_SETTINGS: Int = 0x0e
  private val HPROF_LOCKSTATS_WAIT_TIME: Int = 0x10
  private val HPROF_LOCKSTATS_HOLD_TIME: Int = 0x11
  private val HPROF_GC_ROOT_UNKNOWN: Int = 0xff
  private val HPROF_GC_ROOT_JNI_GLOBAL: Int = 0x01
  private val HPROF_GC_ROOT_JNI_LOCAL: Int = 0x02
  private val HPROF_GC_ROOT_JAVA_FRAME: Int = 0x03
  private val HPROF_GC_ROOT_NATIVE_STACK: Int = 0x04
  private val HPROF_GC_ROOT_STICKY_CLASS: Int = 0x05
  private val HPROF_GC_ROOT_THREAD_BLOCK: Int = 0x06
  private val HPROF_GC_ROOT_MONITOR_USED: Int = 0x07
  private val HPROF_GC_ROOT_THREAD_OBJ: Int = 0x08
  private val HPROF_GC_CLASS_DUMP: Int = 0x20
  private val HPROF_GC_INSTANCE_DUMP: Int = 0x21
  private val HPROF_GC_OBJ_ARRAY_DUMP: Int = 0x22
  private val HPROF_GC_PRIM_ARRAY_DUMP: Int = 0x23
  private val HPROF_HEAP_DUMP_SEGMENT: Int = 0x1c
  private val HPROF_HEAP_DUMP_END: Int = 0x2c
  private val T_CLASS: Int = 2

  def fieldTypeFromTypeId(typeId: Byte): FieldType = {
    typeId match {
      case T_CLASS => ObjectFieldType
      case T_BOOLEAN => BooleanFieldType
      case T_CHAR => CharFieldType
      case T_FLOAT => FloatFieldType
      case T_DOUBLE => DoubleFieldType
      case T_BYTE => ByteFieldType
      case T_SHORT => ShortFieldType
      case T_INT => IntFieldType
      case T_LONG => LongFieldType
      case _ =>
        throw new IOException("Invalid type id of " + typeId)
    }
  }

  /**
   * It seems the HPROF file writes the length field as an unsigned int.
   */
  val MAX_UNSIGNED_4BYTE_INT: Long = 4294967296L
}

//***************************************************************************************************************
class HprofReader(fileName: String) extends SLF4JLogging {
  import HprofReader._
  /**
   * It seems the HPROF spec only allows 4 bytes for record length, so a
   * record length greater than 4GB will be overflowed and will be useless and
   * throw off the rest of the processing. There's no good way to tell the
   * overflow has occurred but if the strictness preference has been set to
   * permissive, we can check the most common case of a heap dump record that
   * should run to the end of the file.
   *
   * @param fileSize The total file size.
   * @param curPos   The current position of the input stream.
   * @param record   The record identifier.
   * @param length   The length read from the record.
   * @return The updated length or the original length if no update is made.
   */
  protected def updateLengthIfNecessary(fileSize: Long, curPos: Long, record: Int, length: Long): Long = {
    var res = length
    if (record == HPROF_HEAP_DUMP) {
      val bytesLeft: Long = fileSize - curPos - 9
      if (bytesLeft >= MAX_UNSIGNED_4BYTE_INT) {
        if ((bytesLeft - length) % MAX_UNSIGNED_4BYTE_INT == 0) {
          log.warn("LENGTH OVERFLOW adjustment")
          res = bytesLeft
        }
      }
    }
    res
  }

  def readFile(dumpVisitor : DumpVisitor, skipFields: Boolean, skipPrimitiveArrays: Boolean) {

    val file = new File(fileName)
    val in = PositionDataInputStream(new BufferedInputStream(new FileInputStream(file),1048576 * 16))
    try {
      // read the first 4 bytes
      if(in.readInt() != MAGIC_NUMBER)
        throw new IllegalArgumentException("File does not start with correct HProf header")

      // read and skip the version header
      readVersionHeader(in)

      val identifierSize = in.readInt

      if (identifierSize != 4 && identifierSize != 8)
        throw new IOException(s"Unknown identifier size: $identifierSize, expected 4 or 8")

      val fileSize = file.length
      val reader = new DataReader(in,identifierSize)

      dumpVisitor.identifierSize(identifierSize)

      // read the creation time
      val creationDate = new Date(in.readLong)
      dumpVisitor.creationDate(creationDate)

      var curPos: Long = in.position

      while (curPos < fileSize) {
        val itemType = in.readUnsignedByte
        in.readInt
        val position = in.position
        // each record at the root level defines it length
        val readLength: Long = in.readUnsignedInt

        val length = updateLengthIfNecessary(fileSize, curPos, itemType, readLength)

        if(log.isDebugEnabled)
          log.debug(s"Read record type $itemType, length $length at position ${Misc.toHex(position)}")

        if (length < 0)
          throw new IOException(s"Bad record length of $length at byte ${Misc.toHex(position + 5)} of file.")

        itemType match {
          case HPROF_UTF8 =>
            val id = reader.readID
            val str = reader.readBoundedString((length - reader.identifierSize).asInstanceOf[Int])
            dumpVisitor.utf8String(id,str)
          case HPROF_LOAD_CLASS =>
            val classSerialNo = reader.readInt
            val classID = reader.readHeapId
            val stackTraceSerialNo = reader.readInt
            val classNameID = reader.readID
            dumpVisitor.loadClass(classSerialNo,classID,stackTraceSerialNo,classNameID)
          case HPROF_HEAP_DUMP =>
            readHeapDumpSegment(length,reader,dumpVisitor,identifierSize, skipFields, skipPrimitiveArrays)
          case HPROF_HEAP_DUMP_SEGMENT =>
            readHeapDumpSegment(length,reader,dumpVisitor,identifierSize, skipFields, skipPrimitiveArrays)
          case HPROF_HEAP_DUMP_END =>
            dumpVisitor.heapDumpEnd()
          case HPROF_STACK_FRAME =>
            val id: Long = reader.readID
            val methodNameId = reader.readID
            val methodSigId = reader.readID
            val sourceFileNameId = reader.readID
            val classSer = reader.readInt
            val lineNumber = reader.readInt
            dumpVisitor.stackFrame(id,methodNameId,methodSigId,sourceFileNameId,classSer,lineNumber)
          case HPROF_STACK_TRACE =>
            val serialNo = reader.readInt
            val threadSerialNo = reader.readInt
            val noFrames = reader.readInt

            val frameIDs = (for( i <- 1 to noFrames) yield reader.readID ).toVector
            dumpVisitor.stackTrace(serialNo,threadSerialNo,frameIDs)
          case HPROF_UNLOAD_CLASS =>
            val classSerialNo = reader.readInt
            dumpVisitor.unloadClass(classSerialNo)
          case HPROF_ALLOC_SITES => reader.skipBytes(length)
          case HPROF_START_THREAD =>
            reader.skipBytes(length)
          case HPROF_END_THREAD => reader.skipBytes(length)
          case HPROF_HEAP_SUMMARY =>
            val totalLiveBytes = reader.readInt
            val totalLiveInstances = reader.readInt
            val totalBytesAllocated = reader.readLong
            val totalInstancesAllocated = reader.readLong
            dumpVisitor.heapSummary(totalLiveBytes,totalLiveInstances,totalBytesAllocated,totalInstancesAllocated)
          case HPROF_CPU_SAMPLES => reader.skipBytes(length)
          case HPROF_CONTROL_SETTINGS => reader.skipBytes(length)
          case HPROF_LOCKSTATS_WAIT_TIME => reader.skipBytes(length)
          case HPROF_LOCKSTATS_HOLD_TIME => reader.skipBytes(length)
          case _ =>
            reader.skipBytes(length)
            log.warn(s"Ignoring unrecognized record type $itemType")
        }


        curPos = in.position
      }
    }finally {
      in.close()
    }
  }

  private def readInstance(reader : DataReader,dumpVisitor : DumpVisitor, identifierSize : Int,
                           skipFields: Boolean, skipPrimitiveArrays: Boolean): Unit = {
    val id = reader.readHeapId
    val stackTraceSerialId = reader.readInt
    val classID = reader.readHeapId
    val fieldDataBlockSize = reader.readInt

    val fields = if(skipFields) {
      reader.skipBytes(fieldDataBlockSize)
      None
    } else {
      val rawFieldData = reader.readBytes(fieldDataBlockSize)

      val dataReader = new DataReader(PositionDataInputStream(new ByteArrayInputStream(rawFieldData)),identifierSize)

      // we can only read the fields if the dump visitor is able to supply the class field information
      // this typically comes from an earlier pass.
      val fieldSigs = dumpVisitor.getClassFieldInfo(classID)
      fieldSigs match {
        case Some(sigs : List[FieldType]) => Some(readInstanceFieldsFields(sigs,dataReader))
        case None => None
      }
    }

    dumpVisitor.instanceDump(id,stackTraceSerialId,classID,fields,fieldDataBlockSize)
  }

  private def readClass(reader : DataReader,dumpVisitor : DumpVisitor, identifierSize : Int,
                        skipFields: Boolean, skipPrimitiveArrays: Boolean): Unit = {
    val id = reader.readHeapId
    val stackTraceSerialId = reader.readInt
    val superClassId = reader.readHeapId
    val classLoaderId = reader.readHeapId
    val signersId = reader.readHeapId
    val protDomainId = reader.readHeapId
    reader.readHeapId // reserved (unused)
    reader.readHeapId // reserved (unused)
    val instanceSize  = reader.readInt
    val numConstPoolEntries = reader.readUnsignedShort
    val constPoolEntries : Map[Int,Any] = (for (i <- 1 to numConstPoolEntries) yield {
      val constantPoolIdx = reader.readUnsignedShort
      val itemType = reader.readByte
      val value = readValueForType(reader,itemType)
      (constantPoolIdx,value)
    })(scala.collection.breakOut)

    val numStatics = reader.readUnsignedShort
    val staticItems: List[ClassStaticEntry] = (for ( k <- 1 to numStatics) yield {
      val nameId = reader.readID
      val itemTypeRaw = reader.readByte
      val itemType = HprofReader.fieldTypeFromTypeId(itemTypeRaw)

      val value = readValueForType(reader,itemTypeRaw)
      new ClassStaticEntry(nameId,itemType,value)
    })(scala.collection.breakOut)

    val numFields: Int = reader.readUnsignedShort
    val fieldItems = (for (j <- 1 to  numFields) yield {
      val nameId = reader.readID
      val itemTypeByte = reader.readByte
      val itemType = HprofReader.fieldTypeFromTypeId(itemTypeByte)
      new ClassFieldEntry(nameId,itemType)
    }).toList

    dumpVisitor.classDump(id,stackTraceSerialId,superClassId,classLoaderId,signersId,protDomainId,instanceSize,
      constPoolEntries,staticItems,fieldItems)
  }
  private def readHeapDumpSegment(segmentLength: Long,reader : DataReader,dumpVisitor : DumpVisitor,
                                  identifierSize : Int, skipFields: Boolean, skipPrimitiveArrays: Boolean) {

    val endPos = reader.position + segmentLength
    while (reader.position < endPos) {

      val itemType = reader.readUnsignedByte

      if(log.isDebugEnabled)
        log.debug(s"    Read heap sub-record type $itemType, at position ${reader.position}")

      itemType match {
        case HPROF_GC_INSTANCE_DUMP =>
          readInstance(reader, dumpVisitor, identifierSize, skipFields, skipPrimitiveArrays)
        case HPROF_GC_CLASS_DUMP =>
          readClass(reader, dumpVisitor, identifierSize, skipFields, skipPrimitiveArrays)
        case HPROF_GC_ROOT_UNKNOWN =>
          val id = reader.readID
          dumpVisitor.gcRootUnknown(id)
        case HPROF_GC_ROOT_THREAD_OBJ =>
          val id = reader.readID
          val threadSeq = reader.readInt
          val stackSeq = reader.readInt
          dumpVisitor.gcRootThreadObj(id,threadSeq,stackSeq)
        case HPROF_GC_ROOT_JNI_GLOBAL =>
          val id: Long = reader.readID
          val jniGlobalRefId = reader.readID
          dumpVisitor.gcRootJNIGlobal(id,jniGlobalRefId)
        case HPROF_GC_ROOT_JNI_LOCAL =>
          val id  = reader.readHeapId
          val threadSeq = reader.readInt
          val depth = reader.readInt
          dumpVisitor.gcRootJNILocal(id,threadSeq,depth)
        case HPROF_GC_ROOT_JAVA_FRAME =>
          val id = reader.readHeapId
          val threadSeq = reader.readInt
          val depth = reader.readInt
          dumpVisitor.gcRootJavaFrame(id,threadSeq,depth)
        case HPROF_GC_ROOT_NATIVE_STACK =>
          val id = reader.readHeapId
          val threadSeq = reader.readInt
          dumpVisitor.gcRootNativeStack(id,threadSeq)
        case HPROF_GC_ROOT_STICKY_CLASS =>
          val id = reader.readID
          dumpVisitor.gcRootStickyClass(id)
        case HPROF_GC_ROOT_THREAD_BLOCK =>
          val id = reader.readID
          val threadSeq = reader.readInt
          dumpVisitor.gcRootThreadBlock(id,threadSeq)
        case HPROF_GC_ROOT_MONITOR_USED =>
          val id = reader.readID
          dumpVisitor.gcRootMonitorUsed(id)
        case HPROF_GC_OBJ_ARRAY_DUMP =>
          val id = reader.readHeapId
          val stackTraceSerialId = reader.readInt
          val numElements = reader.readInt
          val elementClassID = reader.readHeapId
          val elements = for(i <- 1 to numElements) yield HeapId(reader.readID)
          dumpVisitor.objectArrayDump(id,stackTraceSerialId,numElements,elementClassID,elements)
        case HPROF_GC_PRIM_ARRAY_DUMP =>
          readPrimitiveArray(reader,dumpVisitor, skipPrimitiveArrays)
        case _ =>
          throw new IOException("Unrecognized heap dump sub-record type:  " + itemType)
      }
    }

    log.debug("    Finished heap sub-records.")
  }

  private def readVersionHeader(in : PositionDataInputStream) : Int = {
    val buffer = new ListBuffer[Char]()
    var c : Char = in.readByte().asInstanceOf[Char]
    while(c != 0) {
      buffer.append(c)
      c = in.readByte().asInstanceOf[Char]
    }

    // trim the version string - seen version 1.0.2 with leading spaces
    val versionStr = new String(buffer.toArray).trim

    if(versionStr == VERSION_1_0_1)
      1
    else if(versionStr == VERSION_1_0_2)
      2
    else
      throw new IOException("Unrecognized version string: " + versionStr)
  }

  def readValueForType(reader : DataReader,itemTypeInit: Byte) : Any = {
    val itemType = fieldTypeFromTypeId(itemTypeInit)

    readValueForTypeSignature(reader,itemType)
  }

  def readValueForTypeSignature(reader : DataReader,fieldType : FieldType)  = {
    fieldType match {
      case ObjectFieldType => HeapId(reader.readID)
      case BooleanFieldType => reader.readBoolean
      case ByteFieldType => reader.readByte
      case ShortFieldType => reader.readShort
      case CharFieldType => reader.readChar
      case IntFieldType => reader.readInt
      case LongFieldType => reader.readLong
      case FloatFieldType => reader.readFloat
      case DoubleFieldType => reader.readDouble
    }
  }

  def readInstanceFieldsFields(signatures : List[FieldType],fieldReader : DataReader) : Vector[Any] = {
    signatures.map(readValueForTypeSignature(fieldReader,_)).toVector
  }

  private def readPrimitiveArray(reader : DataReader,dumpVisitor : DumpVisitor, skipPrimitiveArrays: Boolean) {
    val id = reader.readHeapId
    val stackTraceSerialID = reader.readInt
    val numElements = reader.readInt

    val elementClassID : Byte = reader.readByte
    val fieldType: BaseFieldType = fieldTypeFromTypeId(elementClassID) match {
      case ObjectFieldType =>
        throw new IllegalStateException("Primitive array type cannot object type")
      case t : BaseFieldType => t
    }

    val data = readPrimitiveArray(reader,numElements,fieldType, skipPrimitiveArrays)

    dumpVisitor.primitiveArray(id,stackTraceSerialID,fieldType,data)
  }

  def readPrimitiveArray(reader : DataReader,numElements : Int,fieldType : BaseFieldType, skipPrimitiveArrays: Boolean): Seq[AnyVal] = {
    if(skipPrimitiveArrays) {
      fieldType match {
        case BooleanFieldType => reader.skipBytes(numElements * 1)
        case ByteFieldType => reader.skipBytes(numElements * 1)
        case CharFieldType => reader.skipBytes(numElements * 2)
        case ShortFieldType => reader.skipBytes(numElements * 2)
        case IntFieldType => reader.skipBytes(numElements * 4)
        case LongFieldType => reader.skipBytes(numElements * 8)
        case FloatFieldType => reader.skipBytes(numElements * 4)
        case DoubleFieldType => reader.skipBytes(numElements * 8)
      }
      Seq.empty
    }
    else
    for(i <- 0 until numElements)  yield fieldType match {
      case BooleanFieldType => reader.readBoolean
      case ByteFieldType => reader.readByte
      case CharFieldType => reader.readChar
      case ShortFieldType => reader.readShort
      case IntFieldType => reader.readInt
      case LongFieldType => reader.readLong
      case FloatFieldType => reader.readFloat
      case DoubleFieldType => reader.readDouble
    }
  }

}