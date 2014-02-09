package org.shelmet.heap.parser

import org.shelmet.heap.util.Misc
import java.io._
import ArrayTypeCodes._
import scala.collection.mutable.ListBuffer
import com.typesafe.scalalogging.slf4j.Logging
import java.util.Date
import org.shelmet.heap.shared._
import org.shelmet.heap.HeapId
import scala.collection.mutable


class Ref(val id: Int) extends AnyVal {
  def isNull = id == 0
}
object Ref {
  def apply(id: Int): Ref = new Ref(id)
}

//class Index(val id: Int) extends AnyVal
//object Index {
//  def apply(id: Int) = new Index(id)
//}

class NameId(val id: Long) extends AnyVal
object NameId {
  def apply(id: HeapId) = new NameId(id.id)
}

class ClassId(val id: Long) extends AnyVal
object ClassId {
  def apply(id: HeapId) = new ClassId(id.id)
}


case class Field(name: String, fieldType: FieldType) {
  def isRef = fieldType.isObjectType
}

// ignoring everything I don't care about for now
// ideally this would be an interim object and the version with the full
// fields would be passed around after the initial scan
case class Clazz(name: ClassType, fields: List[Field]) {
  def types = fields map { _.fieldType }
  def fullFieldName(i: Int) = name + "." + fields(i).name
}

trait DumpVisitorAlt {
  def creationDate(date: Date) {}
  def identifierSize(size: Int) {}
  def heapSummary(totalLiveBytes: Int, totalLiveInstances: Int, totalBytesAllocated: Long, totalInstancesAllocated: Long) {}
  def heapDumpEnd() {}

  def utf8String(id: NameId, string: String) {}
  def loadClass(id: ClassId, name: NameId) {}

//  def gcRootUnknown(id: Long) {}
//  def gcRootThreadObj(id: Long, threadSeq: Int, stackSeq :Int) {}
//  def gcRootJNIGlobal(id: Long, jniGlobalRefId: Long) {}
//  def gcRootJNILocal(heapRef: HeapId, threadSeq: Int, depth: Int) {}
//  def gcRootJavaFrame(heapRef: HeapId, threadSeq: Int, depth: Int) {}
//  def gcRootNativeStack(id: HeapId, threadSeq: Int) {}
//  def gcRootStickyClass(id: Long) {}
//  def gcRootThreadBlock(id: Long, threadSeq: Int) {}
//  def gcRootMonitorUsed(id: Long) {}
//  def unloadClass(classSerialNo: Int) {}
//  def stackFrame(id: Long, methodNameId: Long, methodSigId: Long, sourceFileNameId: Long, classSerialId: Int, lineNo: Int) {}
//  def stackTrace(serialNo: Int, threadSerialNo: Int, frameIDs: Vector[Long]) {}


  def classDump(id: ClassId, 
                superClassId: ClassId, 
                instances: Int, 
//                constPoolEntries: Map[Int, Any], 
                staticItems: List[ClassStaticEntry], 
                fieldItems: List[ClassFieldEntry]) {}

  def instanceDump(id: Ref, classId: ClassId, fields: Vector[Any]) {}
  def objectArrayDump(id: Ref, classId: ClassId, elementIDs: Seq[Ref]) {}
  def primitiveArray(id: Ref, fieldType: BaseFieldType, data: Seq[AnyVal]) {}
}


/**
 * Object that's used to read a hprof file.
 */
object HprofReaderAlt extends Logging {
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
}

//***************************************************************************************************************
class HprofReaderAlt(file: File) extends Logging {
  import HprofReaderAlt._

  // HeapId => Ref
  private val refs = debox.map.Map.empty[Long, Int]
  refs(0) = 0

  private def getRef(heapId: HeapId): Ref = {
    if (refs.contains(heapId.id)) Ref(refs(heapId.id))
    else {
      val ref = Ref(refs.length)
      refs(heapId.id) = ref.id
      ref
    }
  }

  lazy val classes = {
    val visitor = new CountingVisitor
    readFile(visitor, 0)
    visitor.lookup
  }

  lazy val prepped = {
    require(!classes.isEmpty)
    readFile(new DumpVisitorAlt {}, 1)
    true
  }

  lazy val refCount = {
    require(prepped)
    refs.length
  }

  def readFile(dumpVisitor: DumpVisitorAlt, phase: Int = 2) {
    require(phase < 2 || prepped)
    logger.info("phase " + phase)

    val in = PositionDataInputStream(new BufferedInputStream(new FileInputStream(file)))
    try {
      // read the first 4 bytes
      if(in.readInt() != MAGIC_NUMBER)
        throw new IllegalArgumentException("File does not start with correct HProf header")

      // read and skip the version header
      readVersionHeader(in)

      val identifierSize = in.readInt

      if (identifierSize != 4 && identifierSize != 8)
        throw new IOException(s"Unknown identifier size: $identifierSize, expected 4 or 8")

      val reader = new DataReader(in, identifierSize)

      dumpVisitor.identifierSize(identifierSize)

      // read the creation time
      val creationDate = new Date(in.readLong)
      dumpVisitor.creationDate(creationDate)

      while (in.available() != 0) {
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
            val id = NameId(reader.readHeapId)
            val str = reader.readBoundedString(length - reader.identifierSize)
            dumpVisitor.utf8String(id, str)
          case HPROF_LOAD_CLASS =>
            val classSerialNo = reader.readInt
            val classID = ClassId(reader.readHeapId)
            val stackTraceSerialNo = reader.readInt
            val classNameID = NameId(reader.readHeapId)
            dumpVisitor.loadClass(classID, classNameID)
          case HPROF_HEAP_DUMP | HPROF_HEAP_DUMP_SEGMENT =>
            readHeapDumpSegment(length, reader, dumpVisitor, identifierSize, phase)
          case HPROF_HEAP_DUMP_END =>
            dumpVisitor.heapDumpEnd()
          case HPROF_STACK_FRAME =>
            reader.skipBytes(length)
//            val id: Long = reader.readID
//            val methodNameId = reader.readID
//            val methodSigId = reader.readID
//            val sourceFileNameId = reader.readID
//            val classSer = reader.readInt
//            val lineNumber = reader.readInt
//            dumpVisitor.stackFrame(id, methodNameId, methodSigId, sourceFileNameId, classSer, lineNumber)
          case HPROF_STACK_TRACE =>
            reader.skipBytes(length)
//            val serialNo = reader.readInt
//            val threadSerialNo = reader.readInt
//            val noFrames = reader.readInt
//            val frameIDs = (for( i <- 1 to noFrames) yield reader.readID ).toVector
//            dumpVisitor.stackTrace(serialNo, threadSerialNo, frameIDs)
          case HPROF_UNLOAD_CLASS =>
            reader.skipBytes(length)
//            val classSerialNo = reader.readInt
//            dumpVisitor.unloadClass(classSerialNo)
          case HPROF_ALLOC_SITES => reader.skipBytes(length)
          case HPROF_START_THREAD =>
            reader.skipBytes(length)
          case HPROF_END_THREAD => reader.skipBytes(length)
          case HPROF_HEAP_SUMMARY =>
            // is this definitely an INT?
            val totalLiveBytes = reader.readInt
            val totalLiveInstances = reader.readInt
            val totalBytesAllocated = reader.readLong
            val totalInstancesAllocated = reader.readLong
            dumpVisitor.heapSummary(totalLiveBytes, totalLiveInstances, totalBytesAllocated, totalInstancesAllocated)
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

  private def readHeapDumpSegment(segmentLength: Long, reader: DataReader, dumpVisitor: DumpVisitorAlt, identifierSize: Int, phase: Int) {

    val endPos = reader.position + segmentLength
    while (reader.position < endPos) {

      val itemType = reader.readUnsignedByte

      logger.debug(s"    Read heap sub-record type $itemType, at position ${reader.position}")

      itemType match {
        case HPROF_GC_INSTANCE_DUMP =>
          val heapId = reader.readHeapId
          val stackTraceSerialId = reader.readInt
          val classID = ClassId(reader.readHeapId)
          val fieldDataBlockSize = reader.readInt
          val rawFieldData = reader.readBytes(fieldDataBlockSize)

          // we can only read the fields if the dump visitor is able to supply the class field information
          if (phase > 0) {
            val ref = getRef(heapId)
            val sigs = classes(classID).types
            val dataReader = new DataReader(PositionDataInputStream(new ByteArrayInputStream(rawFieldData)), identifierSize)
            dumpVisitor.instanceDump(ref, classID, readInstanceFieldsFields(sigs, dataReader))
          }

        case HPROF_GC_CLASS_DUMP =>
          val id = ClassId(reader.readHeapId)
          val stackTraceSerialId = reader.readInt
          val superClassId = ClassId(reader.readHeapId)
          val classLoaderId = reader.readHeapId
          val signersId = reader.readHeapId
          val protDomainId = reader.readHeapId
          reader.readHeapId // reserved (unused)
          reader.readHeapId // reserved (unused)
        val instanceSize  = reader.readInt
          val numConstPoolEntries = reader.readUnsignedShort
          val constPoolEntries: Map[Int, Any] = (for (i <- 1 to numConstPoolEntries) yield {
            val constantPoolIdx = reader.readUnsignedShort
            val itemType = reader.readByte
            val value = readValueForType(reader, itemType)
            (constantPoolIdx, value)
          }).toMap

          val numStatics = reader.readUnsignedShort
          val staticItems = (for ( k <- 1 to numStatics) yield {
            val nameId = reader.readID
            val itemTypeRaw = reader.readByte
            val itemType = HprofReader.fieldTypeFromTypeId(itemTypeRaw)

            val value = readValueForType(reader, itemTypeRaw)
            new ClassStaticEntry(nameId, itemType, value)
          }).toList

          val numFields: Int = reader.readUnsignedShort
          val fieldItems = (for (j <- 1 to  numFields) yield {
            val nameId = reader.readID
            val itemTypeByte = reader.readByte
            val itemType = HprofReader.fieldTypeFromTypeId(itemTypeByte)
            new ClassFieldEntry(nameId, itemType)
          }).toList

          dumpVisitor.classDump(id, superClassId, instanceSize, /*constPoolEntries,*/ staticItems, fieldItems)
        case HPROF_GC_ROOT_UNKNOWN =>
          val id = reader.readID
//          dumpVisitor.gcRootUnknown(id)
        case HPROF_GC_ROOT_THREAD_OBJ =>
          val id = reader.readID
          val threadSeq = reader.readInt
          val stackSeq = reader.readInt
//          dumpVisitor.gcRootThreadObj(id, threadSeq, stackSeq)
        case HPROF_GC_ROOT_JNI_GLOBAL =>
          val id: Long = reader.readID
          val jniGlobalRefId = reader.readID
//          dumpVisitor.gcRootJNIGlobal(id, jniGlobalRefId)
        case HPROF_GC_ROOT_JNI_LOCAL =>
          val id  = reader.readHeapId
          val threadSeq = reader.readInt
          val depth = reader.readInt
//          dumpVisitor.gcRootJNILocal(id, threadSeq, depth)
        case HPROF_GC_ROOT_JAVA_FRAME =>
          val id = reader.readHeapId
          val threadSeq = reader.readInt
          val depth = reader.readInt
//          dumpVisitor.gcRootJavaFrame(id, threadSeq, depth)
        case HPROF_GC_ROOT_NATIVE_STACK =>
          val id = reader.readHeapId
          val threadSeq = reader.readInt
//          dumpVisitor.gcRootNativeStack(id, threadSeq)
        case HPROF_GC_ROOT_STICKY_CLASS =>
          val id = reader.readID
//          dumpVisitor.gcRootStickyClass(id)
        case HPROF_GC_ROOT_THREAD_BLOCK =>
          val id = reader.readID
          val threadSeq = reader.readInt
//          dumpVisitor.gcRootThreadBlock(id, threadSeq)
        case HPROF_GC_ROOT_MONITOR_USED =>
          val id = reader.readID
//          dumpVisitor.gcRootMonitorUsed(id)
        case HPROF_GC_OBJ_ARRAY_DUMP =>
          val id = reader.readHeapId
          val stackTraceSerialId = reader.readInt
          val numElements = reader.readInt
          val elementClassID = ClassId(reader.readHeapId)
          val elements = for(i <- 1 to numElements) yield getRef(reader.readHeapId)

          val ref = getRef(id)
          dumpVisitor.objectArrayDump(ref, elementClassID, elements)

        case HPROF_GC_PRIM_ARRAY_DUMP =>
          readPrimitiveArray(reader, dumpVisitor)
        case _ => {
          throw new IOException("Unrecognized heap dump sub-record type:  " + itemType)
        }
      }
    }

    logger.debug("    Finished heap sub-records.")
  }

  private def readVersionHeader(in: PositionDataInputStream): Int = {
    val buffer = new ListBuffer[Char]()
    var c: Char = in.readByte().asInstanceOf[Char]
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

  def readValueForType(reader: DataReader, itemTypeInit: Byte): Any = {
    val itemType = fieldTypeFromTypeId(itemTypeInit)

    readValueForTypeSignature(reader, itemType)
  }

  def readValueForTypeSignature(reader: DataReader, fieldType: FieldType)  = {
    fieldType match {
      case ObjectFieldType => getRef(reader.readHeapId)
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

  def readInstanceFieldsFields(signatures: List[FieldType], fieldReader: DataReader): Vector[Any] = {
    signatures.map(readValueForTypeSignature(fieldReader, _)).toVector
  }

  private def readPrimitiveArray(reader: DataReader, dumpVisitor: DumpVisitorAlt) {
    val id = reader.readHeapId
    val stackTraceSerialID = reader.readInt
    val numElements = reader.readInt

    val elementClassID: Byte = reader.readByte
    val fieldType = fieldTypeFromTypeId(elementClassID) match {
      case ObjectFieldType =>
        throw new IllegalStateException("Primitive array type cannot object type")
      case t: BaseFieldType => t
    }

    val data = readPrimativeArray(reader, numElements, fieldType)

    val ref = getRef(id)
    dumpVisitor.primitiveArray(ref, fieldType, data)
  }

  def readPrimativeArray(reader: DataReader, numElements: Int, fieldType: FieldType): Seq[AnyVal] = {
    for(i <- 0 until numElements)  yield fieldType match {
      case BooleanFieldType => reader.readBoolean
      case ByteFieldType => reader.readByte
      case CharFieldType => reader.readChar
      case ShortFieldType => reader.readShort
      case IntFieldType => reader.readInt
      case LongFieldType => reader.readLong
      case FloatFieldType => reader.readFloat
      case DoubleFieldType => reader.readDouble
      case _ =>
        throw new RuntimeException("unknown primitive type?")
    }
  }

}


class CountingVisitor extends DumpVisitorAlt with Logging {
  // relatively small objects, cleanliness of type safety
  // is better than negligible debox performance improvement
  private val classNames = mutable.HashMap[ClassId, NameId]()
  private val names = mutable.HashMap[NameId, String]() withDefault ("unknown@" + _)
  private val classes = mutable.HashMap[ClassId, LazyClazz]()

  def lookup = classes.mapValues(_.toClazz(classes)).toMap

  override def utf8String(id: NameId, string: String) {
    names += id -> string
  }

  // presumably all the loadClass calls happen at the start
  override def loadClass(id: ClassId, name: NameId) {
    classNames += id -> name
  }

  case class LazyClazz(name: ClassType, superClass: ClassId, fields: List[Field]) {
    def allFields(lookup: collection.Map[ClassId, LazyClazz]): List[Field] = fields ::: {
      lookup.get(superClass) match {
        case None => Nil
        case Some(inherited) => inherited.allFields(lookup)
      }
    }

    def toClazz(lookup: collection.Map[ClassId, LazyClazz]) = Clazz(name, allFields(lookup))
  }

  override def classDump(id: ClassId,
                         superClassId: ClassId,
                         instanceSize: Int,
                         //                         constPoolEntries: Map[Int, Any],
                         staticItems: List[ClassStaticEntry],
                         fieldItems: List[ClassFieldEntry]) {
    val classSig = names(classNames(id))
    val classType = ClassType.parse(classSig)
    val fields = fieldItems.map { fi =>
      val fieldName = names(NameId(HeapId(fi.nameId)))
      Field(fieldName, fi.itemType)
    }
    classes += id -> LazyClazz(classType, superClassId, fields)
  }
}
