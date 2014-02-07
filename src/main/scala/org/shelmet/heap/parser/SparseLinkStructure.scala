package org.shelmet.heap.parser

import com.typesafe.scalalogging.slf4j.Logging
import scala.collection.mutable
import org.shelmet.heap.shared.{FieldType, ClassType}
import org.shelmet.heap.HeapId
import com.google.common.base.Stopwatch
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.concurrent.LinkedBlockingQueue

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
  //  val reader = new HprofReader("sbt.hprof")
  //  val reader = new HprofReader("visualvm.hprof")

  val watch = Stopwatch.createStarted()
  //  reader.readFile(new DumpVisitor {})
  //  logger.info("NULL pass took " + watch)
  //
  //  watch.reset()
  //  watch.start()

  // first pass is just to create a unique ID for each entry
  val countingVisitor = new CountingVisitor
  reader.readFile(countingVisitor)
  logger.info("COUNTING pass took " + watch)

  watch.reset()
  watch.start()
  val linkingVisitor = new LinkingVisitor(countingVisitor.lookup)
  reader.readFile(linkingVisitor)


  val links = linkingVisitor.links
  logger.info("LINKING pass took " + watch)

  logger.info(s"${links.columns } refs with ${links.count } links")

}

case class Lookups(forward: IndexedSeq[Ref],
                   backward: Map[Ref, Index],
                   classes: Map[ClassId, Clazz])


class CountingVisitor extends DumpVisitor with Logging {
  private val classNames = mutable.HashMap[ClassId, NameId]()
  private val names = mutable.HashMap[NameId, String]() withDefault ("unknown@" + _)
  private val classes = mutable.HashMap[ClassId, LazyClazz]()

  def lookup = classes.mapValues(_.toClazz(classes)).toMap

  final override def utf8String(id: Long, string: String) {
    names += (NameId(id) -> string)
  }

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
    classes += ClassId(id) -> LazyClazz(classType, ClassId(superClassId), fields)
  }
}

class LinkingVisitor(classes: Map[ClassId, Clazz]) extends DumpVisitor with Logging {

  val links = new CompressedColumns

  // horrible hack to block the data source
  private val queue = new LinkedBlockingQueue[Any](Runtime.getRuntime.availableProcessors() * 2)
  override def heapDumpEnd() {
    while (queue.peek() != null) {
      Thread.sleep(1000)
    }
  }

  // TODO: randomise work queue to reduce "Matrix.set" contention
  private def add(from: Ref, to: Ref) {
    queue.put(links) // any object works
    Future {
      try {
        links.set(from.id, to.id)
        val tally = links.count
        if (tally % 1000000 == 0)
          logger.info(tally + " links")
      } finally {
        queue.poll()
      }
    } onFailure {
      case t: Throwable => logger.error("", t)
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
              add(Ref(heapId), Ref(to))
            case _ => // primitive
          }
        }
    }
  }

  override def objectArrayDump(heapId: HeapId, stackTraceSerialId: Int, numElements: Int, classId: HeapId, values: Seq[HeapId]) {
    assert(numElements == values.size)

    (0 until values.size) foreach { i =>
      val to = values(i)
      if (!to.isNull)
        add(Ref(heapId), Ref(to))
    }
  }


}
