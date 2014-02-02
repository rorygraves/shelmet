package org.shelmet.heap.parser

import com.typesafe.scalalogging.slf4j.Logging
import scala.collection.mutable
import org.shelmet.heap.shared.{FieldType, BaseFieldType, ClassType}
import org.shelmet.heap.HeapId
import com.google.common.base.Stopwatch

// value classes for Correctness of indexes
// TODO: move to DumpVisitor
// http://docs.scala-lang.org/overviews/core/value-classes.html

class Ref(val id: Long) extends AnyVal {
  def isNull = id == 0
}

object Ref {
  def apply(id: Long): Ref = new Ref(id)
  def apply(heapId: HeapId) = new Ref(heapId.id)
}

class Index(val id: Int) extends AnyVal

object Index {
  def apply(id: Int) = new Index(id)
}

class NameId(val id: Long) extends AnyVal

object NameId {
  def apply(id: Long) = new NameId(id)
}

class ClassId(val id: Long) extends AnyVal

object ClassId {
  def apply(id: Long) = new ClassId(id)
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


object SparseLinkStructure extends App with Logging {

      val reader = new HprofReader("intellij.hprof")
//  val reader = new HprofReader("heap.bin")

  val watch = Stopwatch.createStarted()
  reader.readFile(new DumpVisitor {})
  logger.info("NULL pass took " + watch)

  watch.reset()
  watch.start()
  // first pass is just to create a unique ID for each entry
  val countingVisitor = new CountingVisitor
  reader.readFile(countingVisitor)
  val lookups = countingVisitor.lookups
  logger.info("COUNTING pass took " + watch)

  watch.reset()
  watch.start()
  val linkingVisitor = LinkingVisitor(lookups)
  reader.readFile(linkingVisitor)
  logger.info("LINKING pass took " + watch)

  logger.info(s"${linkingVisitor.forward.size } refs with ${linkingVisitor.count } links")
  logger.info(linkingVisitor.missing.map { _.swap }.toVector.sortWith { _._1 > _._1 }.mkString("\n"))

}

case class Lookups(forward: IndexedSeq[Ref],
                   backward: Map[Ref, Index],
                   classes: Map[ClassId, Clazz])


class CountingVisitor extends DumpVisitor with Logging {
  // experiment with immutable vars
  private val classNames = mutable.HashMap[ClassId, NameId]()
  private val names = mutable.HashMap[NameId, String]() withDefault ("unknown@" + _)
  private val refs = mutable.HashSet[Ref]()
  private val classes = mutable.HashMap[ClassId, LazyClazz]()

  def lookups = {
    val forward = refs.toIndexedSeq
    Lookups(
      forward,
      forward.zipWithIndex.toMap.mapValues { Index(_) },
      classes.mapValues { _.toClazz(classes) }.toMap
    )
  }

  final override def utf8String(id: Long, string: String) {
    names += (NameId(id) -> string)
  }

  private def index(id: Ref) {
    if (!id.isNull && !refs(id))
      refs(id) = true
  }


  // do we need to do anything with the roots?
  //  override def gcRootUnknown(id : Long) = index(Ref(id))
  //  override def gcRootThreadObj(id : Long,threadSeq : Int,stackSeq :Int) = index(Ref(id))
  //  override def gcRootJNIGlobal(id : Long,jniGlobalRefId : Long) = index(Ref(id))
  //  override def gcRootJNILocal(heapRef : HeapId,threadSeq : Int,depth : Int) = index(Ref(heapRef))
  //  override def gcRootJavaFrame(heapRef : HeapId,threadSeq : Int,depth : Int) = index(Ref(heapRef))
  //  override def gcRootNativeStack(id: HeapId,threadSeq : Int) = index(Ref(id))
  //  override def gcRootStickyClass(id : Long) = index(Ref(id))
  //  override def gcRootThreadBlock(id : Long,threadSeq : Int) = index(Ref(id))
  //  override def gcRootMonitorUsed(id : Long) = index(Ref(id))


  override def instanceDump(id: HeapId, stackTraceSerialId: Int, classId: HeapId, fields: Option[Vector[Any]], fieldsLengthBytes: Int) = index(Ref(id))

  override def objectArrayDump(id: HeapId, stackTraceSerialId: Int, numElements: Int, classId: HeapId, elementIDs: Seq[HeapId]) = index(Ref(id))

  override def primitiveArray(heapId: HeapId, stackTraceSerialID: Int, fieldType: BaseFieldType, data: Seq[AnyVal]) = index(Ref(heapId))


  // presumably all the loadClass calls happen at the start
  override def loadClass(classSerialNo: Int, classID: HeapId, stackTraceSerialNo: Int, classNameID: Long) {
    classNames += ClassId(classID) -> NameId(classNameID)
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
    staticItems foreach {
      _.value match {
        case h: HeapId => index(Ref(h))
        case _ => // primitive
      }
    }

    // what are these for?
    constPoolEntries foreach { s => println(s._2.getClass) }

    classes += ClassId(id) -> LazyClazz(classType, ClassId(superClassId), fields)
  }
}

class LinkingVisitor(val forward: IndexedSeq[Ref],
                     val backward: Map[Ref, Index],
                     val classes: Map[ClassId, Clazz]) extends DumpVisitor with Logging {

//  private val links = new LinkedMatrix(forward.size)

//  private var links = List[(Ref, Ref)]()

//  private val links = new TLongObjectHashMap[Set[Ref]]()

  private val links = new mutable.HashMap[Ref, mutable.Set[Ref]] with mutable.MultiMap[Ref, Ref]

  var missing = Map[String, Int]() withDefault (f => 0)

  var count = 0

//  def count = links.size

  private def add(from: Ref, to: Ref)(fail: => String) {
    try {
//      links.set(backward(from).id, backward(to).id, 1.0)
//      links = (from, to) :: links
//      links.put(from.id, to.id)

      links.addBinding(from, to)

      count += 1
      if (count % 100000 == 0)
        logger.info(count + " links")

    } catch {
      case _: NoSuchElementException =>
        val failed = fail
        missing += (failed -> (missing(failed) + 1))
    }
  }

  override def getClassFieldInfo(classHeapId: HeapId): Option[List[FieldType]] =
    classes.get(ClassId(classHeapId)) map { _.types }

  override def instanceDump(heapId: HeapId, stackTraceSerialId: Int, classId: HeapId, fields: Option[Vector[Any]], fieldsLengthBytes: Int) {
    fields match {
      case None =>
      case Some(fields) =>
        val clazz = classes(ClassId(classId))
        assert(fields.size == clazz.fields.size)
        (0 until fields.size) foreach { i =>
          fields(i) match {
            case HeapId(to) if !Ref(to).isNull =>
              add(Ref(heapId), Ref(to))(clazz.fullFieldName(i))
            case _ => // primitive
          }
        }
    }
  }

  override def primitiveArray(heapId: HeapId, stackTraceSerialID: Int, fieldType: BaseFieldType, data: Seq[AnyVal]) {
    // TODO
  }

  override def objectArrayDump(heapId: HeapId, stackTraceSerialId: Int, numElements: Int, classId: HeapId, values: Seq[HeapId]) {
    assert(numElements == values.size)

    (0 until values.size) foreach { i =>
      val to = values(i)
      if (!to.isNull)
        add(Ref(heapId), Ref(to))(classes(ClassId(classId)).name.toString)
    }
  }

}

object LinkingVisitor {
  def apply(lookups: Lookups): LinkingVisitor =
    new LinkingVisitor(lookups.forward, lookups.backward, lookups.classes)
}