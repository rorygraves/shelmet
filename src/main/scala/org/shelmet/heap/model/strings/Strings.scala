package org.shelmet.heap.model.strings

import org.shelmet.heap.parser._
import org.shelmet.heap.model.{JavaField, JavaClass}
import java.io.File
import org.shelmet.heap.model.create.AbstractDumpVisitor
import org.shelmet.heap.HeapId
import org.shelmet.heap.shared.{BaseFieldType, FieldType}
import org.shelmet.heap.model.JavaField
import scala.Some
import org.shelmet.heap.HeapId

//*********************************************************************************************************
object Strings extends App {
  if(args.length != 1)
    throw new IllegalArgumentException("Usage: java org.shelmet.heap.model.strings.Strings <dumpfile>")

  val startTime = System.currentTimeMillis()

  val dumpFile = new File(args(0))
  val reader = new HprofReader(dumpFile.getAbsolutePath)


  println("Discovering java.lang.String heapId")
  val stringClass = findStringClass()

  println(s"StringClass = $stringClass - ${stringClass.heapId}")
  val valueIndex = stringClass.fields.indexWhere(_.name == "value")
  require(valueIndex >= 0)


  println("Discovering string instances")
  val strIdToByteArrIds = findStringAndByteRefs(stringClass)
  val byteIds: Set[HeapId] = strIdToByteArrIds.values.toSet

  println(s"Found ${strIdToByteArrIds.size} strings referring to ${byteIds.size} underlying byte arrays")

  val (byteMap,counts) = byteIdsToStringWithCounts(byteIds)

  println("------------------------------------------------------------------------------")
  val l = counts.toList.sortBy(-_._2) foreach { case (str,count) =>
    println(s"$count $str")
  }

  println("------------------------------------------------------------------------------")
  val endTime = System.currentTimeMillis()
  println(s"Took ${endTime-startTime}ms")

  //*********************************************************************************************************
  def findStringAndByteRefs(stringClass : JavaClass) : Map[HeapId,HeapId] = {
    val fieldTypes = stringClass.fields.map{ f : JavaField => f.fieldType }.toList
    val instVisitor = new StringInstVisitor(stringClass.heapId,fieldTypes,valueIndex)
    reader.readFile(instVisitor)
    instVisitor.stringIdsToByteIds
  }

  //*********************************************************************************************************
  def findStringClass(): JavaClass = {
    val scr = new StringClassVisitor
    reader.readFile(scr)
    scr.stringClass
  }

  //*********************************************************************************************************
  def byteIdsToStringWithCounts(byteIds : Set[HeapId]) : (Map[HeapId, String],Map[String,Int]) = {
    val byteVistor = new StringDataCollector(byteIds)
    reader.readFile(byteVistor)

    (byteVistor.result,byteVistor.counts)
  }
}

//*********************************************************************************************************
class StringDataCollector(byteArrIds : Set[HeapId]) extends AbstractDumpVisitor(false) {
  var result : Map[HeapId,String] = Map.empty
  var counts : Map[String,Int] = Map.empty
  override def primitiveArray(heapId : HeapId,stackTraceSerialID : Int,fieldType : BaseFieldType,data : ArrayWrapper) {
    if(byteArrIds.contains(heapId)) {
      // intern strings to minimise strings process memory, as it is a one off process.
      val str = data.asInstanceOf[CharArrayWrapper].toString.intern()
      result += (heapId -> str)
      counts += (str -> (counts.getOrElse(str,0) + 1))
    }
  }
}

//*********************************************************************************************************
class StringInstVisitor(stringHeapId : HeapId,stringFieldTypes : List[FieldType],valueIndex : Int)  extends AbstractDumpVisitor(false) {
  var stringIdsToByteIds : Map[HeapId,HeapId] = Map.empty

  val sFields = Some(stringFieldTypes)

  override def getClassFieldInfo(classHeapId : HeapId) : Option[List[FieldType]] = {
    if(classHeapId == stringHeapId)
      sFields
    else
      None
  }

  override def instanceDump(id : HeapId,stackTraceSerialId : Int,classId : HeapId,fields : Option[Vector[Any]],
                            fieldsLengthBytes : Int) {
    if(classId == stringHeapId) {
      val bytesRef = fields.get(valueIndex).asInstanceOf[HeapId]
      stringIdsToByteIds += (id -> bytesRef)
    }
  }

}

//*********************************************************************************************************
class StringClassVisitor extends AbstractDumpVisitor(false) {

  var stringClass : JavaClass = null

  override def classDump(id : HeapId,stackTraceSerialId : Int,
                superClassId : HeapId,
                classLoaderId : HeapId,
                signerId : HeapId,
                protDomainId : HeapId,
                instanceSize : Int,
                constPoolEntries : Map[Int,Any],
                staticItems : List[ClassStaticEntry],
                fieldItems : List[ClassFieldEntry]) {
    val className = classNameFromObjectId(id)
    if(className == "java.lang.String") {
      val statics = readStatics(className,staticItems)
      val fields = readFields(className,fieldItems)
      stringClass = new JavaClass(id,className,superClassId,classLoaderId,signerId,protDomainId,statics,
        instanceSize,fields)
    }
  }
}