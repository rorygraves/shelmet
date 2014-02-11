package org.shelmet.heap.parser

import com.typesafe.scalalogging.slf4j.Logging
import scala.collection.mutable
import org.shelmet.heap.shared.{BaseFieldType, FieldType, ClassType}
import org.shelmet.heap.HeapId
import com.google.common.base.Stopwatch
import debox.map.{Map => DMap}
import java.io.{BufferedInputStream, File, FileInputStream}

object SparseLinkStructure extends App with Logging {

  val file = "intellij.hprof"

  val reader = new HprofReader(file)

  def timed[T <: AnyRef](text: String)(f: => T): T = {
    val watch = Stopwatch.createStarted()
    f withEffect { r =>
      logger.info(text + " took " + watch)
    }
  }

  timed("reading full file") {
    // for benchmarking
    val reader = new BufferedInputStream(new FileInputStream(new File(file)))
    try {
      val buffer = Array.ofDim[Byte](1024)
      while (reader.read(buffer) != -1) {}
      "done"
    } finally reader.close()
  }

  timed("null reader") {
    reader.readFile(new DumpVisitor {})
    "done"
  }

  val (classes, refs, refTypes, arrays) = timed("class visitor") {
    val visitor = new ClassVisitor
    reader.readFile(visitor)
    (visitor.lookup, visitor.refs, visitor.refTypes, visitor.arrays)
  }

  logger.info(s"seen ${refs.length } refs and ${arrays.length } arrays")

  val links = timed("linking") {
    // and mutates "refs"
    val visitor = new LinkingVisitor(classes, refs)
    reader.readFile(visitor)
    visitor.links
  }

  logger.info(s"${links.columnSize } refs, ${links.count } links, ${classes.size } classes and ${refs.length - links.columnSize } refs without metadata")

}

class Ref(val id: Int) extends AnyVal {
  def isNull = id == 0
}

object Ref {
  def apply(id: Int): Ref = new Ref(id)
}


class NameId(val id: Long) extends AnyVal

object NameId {
  def apply(id: Long) = new NameId(id)
}

class ClassId(val id: Long) extends AnyVal

object ClassId {
  //  def apply(id: Long) = new ClassId(id)
  def apply(id: HeapId) = new ClassId(id.id)
}


case class Field(name: String, fieldType: FieldType) {
  def isRef = fieldType.isObjectType
}

// ignoring everything I don't care about for now
// ideally this would be an interim object and the version with the full
// fields would be passed around after the initial scan
case class Clazz(name: ClassType, fields: List[Field]) {
  def types = fields map {
    _.fieldType
  }

  def fullFieldName(i: Int) = name + "." + fields(i).name
}


trait RefRecorder {
  // HeapId => Ref
  def refs: DMap[Long, Int]

  def getRef(id: HeapId) =
    if (id.isNull) Ref(0)
    else Ref(refs.get(id.id) match {
      case None =>
        val ref = refs.length
        refs(id.id) = ref
        ref
      case Some(ref) => ref
    })
}


// first pass
// we only care about creating Refs for the references that have a heap dump segment at this stage
// as they are the only things we care about for the linking
class ClassVisitor extends DumpVisitor with RefRecorder with Logging {
  // relatively small objects, cleanliness of type safety
  // is better than negligible debox performance improvement
  private val classNames = mutable.HashMap[ClassId, NameId]()
  private val names = mutable.HashMap[NameId, String]() withDefault ("unknown@" + _)
  private val classes = mutable.HashMap[ClassId, LazyClazz]()

  def lookup = classes.mapValues(_.toClazz(classes)).toMap

  val refs = DMap.empty[Long, Int]
  refs(0) = 0

  override def utf8String(id: Long, string: String) {
    names += NameId(id) -> string
  }

  // presumably all the loadClass calls happen at the start
  override def loadClass(classSerialNo: Int, classID: HeapId, stackTraceSerialNo: Int, classNameId: Long) {
    classNames += ClassId(classID) -> NameId(classNameId)
    //getRef(classID) // classes can be referenced, but is this useful for the linking?
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

  override def classDump(id: HeapId, stackTraceSerialId: Int,
                         superClassId: HeapId,
                         classLoaderId: HeapId,
                         signerId: HeapId,
                         protDomainId: HeapId,
                         instanceSize: Int,
                         constPoolEntries: Map[Int, Any],
                         staticItems: List[ClassStaticEntry],
                         fieldItems: List[ClassFieldEntry]) {
    val classSig = names(classNames(ClassId(id)))
    val classType = ClassType.parse(classSig)

    val fields = fieldItems.map { fi =>
      val fieldName = names(NameId(fi.nameId))
      Field(fieldName, fi.itemType)
    }
    // static items can be referenced, but is this useful for the linking?
    // const pool are not referenced so are not linked
    //    staticItems foreach {_.value match {
    //        case id: HeapId if !id.isNull => getRef(id)
    //        case _ =>
    //      }
    //    }
    classes += ClassId(id) -> LazyClazz(classType, ClassId(superClassId), fields)
  }

  // Ref => ClassId
  val refTypes = DMap.empty[Int, ClassId]
  // Ref => array size in bytes
  val arrays = DMap.empty[Int, Int]

  override def instanceDump(id: HeapId, x: Int, classId: HeapId, fields: Option[Vector[Any]], xx: Int) {
    refTypes(getRef(id).id) = ClassId(classId)
  }

  override def objectArrayDump(id: HeapId, x: Int, numElements: Int, classId: HeapId, elementIDs: Seq[HeapId]) {
    arrays(getRef(id).id) = numElements
  }

  override def primitiveArray(id: HeapId, x: Int, fieldType: BaseFieldType, data: Seq[AnyVal]) {
    arrays(getRef(id).id) = data.length * fieldType.fieldSize
  }
}


trait ClassAwareDumpVisitor extends DumpVisitor {
  def classes: Map[ClassId, Clazz]

  final override def getClassFieldInfo(classHeapId: HeapId) =
    classes.get(ClassId(classHeapId)) map { c =>
      for {field <- c.fields} yield field.fieldType
    }
}


class LinkingVisitor(val classes: Map[ClassId, Clazz], val refs: DMap[Long, Int]) extends ClassAwareDumpVisitor with RefRecorder with Logging {

  val links = new CompressedColumns(refs.length)

  private def link(from: Ref, to: Ref) {
    if (to.id >= links.columnSize) return

    links.set(from.id, to.id)
    val tally = links.count
    if (tally % 1000000 == 0)
      logger.info(tally + " links")
  }

  override def instanceDump(id: HeapId, x: Int, classId: HeapId, fields: Option[Vector[Any]], xx: Int) {
    val from = getRef(id)
    fields.get foreach {
      _ match {
        case to: HeapId if !to.isNull =>
          link(from, getRef(to))
        case _ => // primitive
      }
    }
  }

  override def objectArrayDump(id: HeapId, x: Int, numElements: Int, classId: HeapId, elementIDs: Seq[HeapId]) {
    val from = getRef(id)
    elementIDs foreach { to =>
      link(from, getRef(to))
    }
  }
}
