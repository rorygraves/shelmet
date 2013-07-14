package org.shelmet.heap.parser

import org.shelmet.heap.util.Misc
import java.io._
import ArrayTypeCodes._
import scala.collection.mutable.ListBuffer
import org.shelmet.heap.HeapId
import com.typesafe.scalalogging.slf4j.Logging
import java.util.Date

/**
 * Object that's used to read a hprof file.
 */
object HprofReader extends Logging {
  val MAGIC_NUMBER: Int = 0x4a415641

  private val VERSION_1_0_1 = " PROFILE 1.0.1"
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

  def signatureFromTypeId(typeId: Byte): Char = {
    typeId match {
      case T_CLASS => 'L'
      case T_BOOLEAN => 'Z'
      case T_CHAR => 'C'
      case T_FLOAT => 'F'
      case T_DOUBLE => 'D'
      case T_BYTE => 'B'
      case T_SHORT => 'S'
      case T_INT => 'I'
      case T_LONG => 'J'
      case _ =>
        throw new IOException("Invalid type id of " + typeId)
    }
  }
}

//***************************************************************************************************************
class HprofReader(fileName: String) extends Logging {
  import HprofReader._
  def readFile(dumpVisitor : DumpVisitor) {

    val in = new PositionDataInputStream(new BufferedInputStream(new FileInputStream(new File(fileName))))
    try {
      // read the first 4 bytes
      if(in.readInt() != MAGIC_NUMBER)
        throw new IllegalArgumentException("File does not start with correct HProf header")

      // read and skip the version header
      readVersionHeader(in)

      val identifierSize = in.readInt

      if (identifierSize != 4 && identifierSize != 8)
        throw new IOException(s"Unknown identifier size: $identifierSize, expected 4 or 8")

      val reader = new DataReader(in,identifierSize)

      dumpVisitor.identifierSize(identifierSize)

      // read the creation time
      val creationDate = new Date(in.readLong)
      dumpVisitor.creationDate(creationDate)

      while (in.available() > 0) {
        val itemType = in.readUnsignedByte
        in.readInt
        val position = in.position
        // each record at the root level defines it length
        val length: Int = (in.readInt & 0xffffffffL).asInstanceOf[Int]
        logger.debug(s"Read record type $itemType, length $length at position ${Misc.toHex(position)}")

        if (length < 0)
          throw new IOException(s"Bad record length of $length at byte ${Misc.toHex(position + 5)} of file.")

        itemType match {
          case HPROF_UTF8 =>
            val id = reader.readID
            val str = reader.readBoundedString(length - reader.identifierSize)
            dumpVisitor.utf8String(id,str)
          case HPROF_LOAD_CLASS =>
            val classSerialNo = reader.readInt
            val classID = reader.readHeapId
            val stackTraceSerialNo = reader.readInt
            val classNameID = reader.readID
            dumpVisitor.loadClass(classSerialNo,classID,stackTraceSerialNo,classNameID)
          case HPROF_HEAP_DUMP =>
            readHeapDumpSegment(length,reader,dumpVisitor,identifierSize)
          case HPROF_HEAP_DUMP_SEGMENT =>
            readHeapDumpSegment(length,reader,dumpVisitor,identifierSize)
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
            logger.warn(s"Ignoring unrecognized record type $itemType")
        }
      }
    }finally {
      in.close()
    }
  }

  private def readHeapDumpSegment(segmentLength: Long,reader : DataReader,dumpVisitor : DumpVisitor,identifierSize : Int) {

    val endPos = reader.position + segmentLength
    while (reader.position < endPos) {

      val itemType = reader.readUnsignedByte

      logger.debug(s"    Read heap sub-record type $itemType, at position ${reader.position}")

      itemType match {
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
        case HPROF_GC_CLASS_DUMP =>
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
          }).toMap

          val numStatics = reader.readUnsignedShort
          val staticItems = (for ( k <- 1 to numStatics) yield {
            val nameId = reader.readID
            val itemTypeRaw = reader.readByte
            val itemType = HprofReader.signatureFromTypeId(itemTypeRaw)

            val value = readValueForType(reader,itemTypeRaw)
            new ClassStaticEntry(nameId,itemType.toChar,value)
          }).toList

          val numFields: Int = reader.readUnsignedShort
          val fieldItems = (for (j <- 1 to  numFields) yield {
            val nameId = reader.readID
            val itemTypeByte = reader.readByte
            val itemType = HprofReader.signatureFromTypeId(itemTypeByte)
            new ClassFieldEntry(nameId,itemType)
          }).toList

          dumpVisitor.classDump(id,stackTraceSerialId,superClassId,classLoaderId,signersId,protDomainId,instanceSize,
            constPoolEntries,staticItems,fieldItems)
        case HPROF_GC_INSTANCE_DUMP =>
          val id = reader.readHeapId
          val stackTraceSerialId = reader.readInt
          val classID = reader.readHeapId
          val bytesFollowing = reader.readInt
          val fieldValues = reader.readBytes(bytesFollowing)
          dumpVisitor.instanceDump(id,stackTraceSerialId,classID,new BlockDataReader(fieldValues,identifierSize))
        case HPROF_GC_OBJ_ARRAY_DUMP =>
          val id = reader.readHeapId
          val stackTraceSerialId = reader.readInt
          val numElements = reader.readInt
          val elementClassID = reader.readHeapId
          val elements = for(i <- 1 to numElements) yield HeapId(reader.readID)
          dumpVisitor.objectArrayDump(id,stackTraceSerialId,numElements,elementClassID,elements)
        case HPROF_GC_PRIM_ARRAY_DUMP =>
          readPrimitiveArray(reader,dumpVisitor)
        case _ => {
          throw new IOException("Unrecognized heap dump sub-record type:  " + itemType)
        }
      }
    }

    logger.debug("    Finished heap sub-records.")
  }

  private def readVersionHeader(in : PositionDataInputStream) : Int = {
    val buffer = new ListBuffer[Char]()
    var c : Char = in.readByte().asInstanceOf[Char]
    while(c != 0) {
      buffer.append(c)
      c = in.readByte().asInstanceOf[Char]
    }

    val versionStr = new String(buffer.toArray)

    if(versionStr == VERSION_1_0_1)
      1
    else if(versionStr == VERSION_1_0_2)
      2
    else
      throw new IOException("Unrecognized version string: " + versionStr)
  }

  def readValueForType(reader : DataReader,itemTypeInit: Byte) : Any = {
    val itemType = signatureFromTypeId(itemTypeInit)

    readValueForTypeSignature(reader,itemType)
  }

  private def readValueForTypeSignature(reader : DataReader,itemType: Char) : Any = {
    itemType match {
      case '[' | 'L' =>
        val id = reader.readID
        HeapId(id)
      case 'Z' => reader.readBoolean
      case 'B' => reader.readByte
      case 'S' => reader.readShort
      case 'C' => reader.readChar
      case 'I' => reader.readInt
      case 'J' => reader.readLong
      case 'F' => reader.readFloat
      case 'D' => reader.readDouble
      case _ =>
        throw new IOException("Bad value signature:  " + itemType)
    }
  }

  private def readPrimitiveArray(reader : DataReader,dumpVisitor : DumpVisitor) {
    val id = reader.readHeapId
    val stackTraceSerialID = reader.readInt
    val numElements = reader.readInt

    val elementClassID : Int = reader.readByte

    val (primitiveSignature,elementSize,data) = readPrimativeArray(reader,numElements,elementClassID)

    dumpVisitor.primitiveArray(id,stackTraceSerialID,primitiveSignature.asInstanceOf[Byte],elementSize,data)
  }

  def readPrimativeArray(reader : DataReader,numElements : Int,elementClassID : Int): (Char,Int,AnyRef) = {
    elementClassID match {
      case T_BOOLEAN =>
        ('Z',1,(for(i <- 0 until numElements) yield reader.readBoolean ).toArray)
      case T_BYTE =>
        ('B',1,(for(i <- 0 until numElements) yield reader.readByte ).toArray)
      case T_CHAR =>
        ('C',2,(for(i <- 0 until numElements) yield reader.readChar ) .toArray)
      case T_SHORT =>
        ('S',2,(for(i <- 0 until numElements) yield reader.readShort ) .toArray)
      case T_INT =>
        ('I',4,(for(i <- 0 until numElements) yield reader.readInt ) .toArray)
      case T_LONG =>
        ('J',8,(for(i <- 0 until numElements) yield reader.readLong ) .toArray)
      case T_FLOAT =>
        ('F',4,(for(i <- 0 until numElements) yield reader.readFloat ) .toArray)
      case T_DOUBLE =>
        ('D',8,(for(i <- 0 until numElements) yield reader.readDouble ) .toArray)
      case _ =>
        throw new RuntimeException("unknown primitive type?")
    }
  }

}