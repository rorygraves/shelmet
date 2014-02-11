package org.shelmet.heap.parser

import com.typesafe.scalalogging.slf4j.Logging
import scala.collection.mutable
import org.shelmet.heap.shared.{BaseFieldType, FieldType, ClassType}
import org.shelmet.heap.HeapId
import com.google.common.base.Stopwatch
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.concurrent.atomic.AtomicLong
import java.util.Date


// for persist to disc
// https://github.com/jankotek/MapDB/blob/master/src/test/java/examples/MultiMap.java

object SparseLinkStructure extends App with Logging {

  val reader = new HprofReader("visualvm.hprof")

  def timed[T <: AnyRef](text: String)(f: => T): T = {
    val watch = Stopwatch.createStarted()
    f withEffect {
      r =>
        logger.info(text + " took " + watch)
    }
  }

  val classes = timed("class visitor") {
    val visitor = new ClassVisitor
    reader.readFile(visitor)
    visitor.lookup
  }

  val refs = timed("ref indexing") {
    val visitor = new RefVisitor(classes)
    reader.readFile(visitor)
    visitor.refs
  }

  logger.info("seen " + refs.length + " references")

  //  val linkingVisitor = new LinkingVisitor(reader.refs.length)
  //  reader.readFile(linkingVisitor)
  //
  //  val links = linkingVisitor.links
  //  logger.info("LINKING pass took " + watch + " with " + reader.refs.length)
  //
  //  logger.info(s"${links.columnSize } refs with ${links.count } links")
  //
  //  logger.info("dumps for " + linkingVisitor.seen.length + " but " + linkingVisitor.known.length + " refs in the link structure")

  //  reader.db.close()

  //  System.gc()
  //  System.gc()
  //  System.gc()
  //  System.gc()
  //  System.gc()
  //  Thread.sleep(30000)
  //
  //  logger.info(s"${links.columnSize } refs with ${links.count } links")

}

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
  def apply(id: Long) = new NameId(id)

  //  def apply(id: HeapId) = new NameId(id.id)
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


// first pass
class ClassVisitor extends DumpVisitor with Logging {
  // relatively small objects, cleanliness of type safety
  // is better than negligible debox performance improvement
  private val classNames = mutable.HashMap[ClassId, NameId]()
  private val names = mutable.HashMap[NameId, String]() withDefault ("unknown@" + _)
  private val classes = mutable.HashMap[ClassId, LazyClazz]()

  def lookup = classes.mapValues(_.toClazz(classes)).toMap

  override def utf8String(id: Long, string: String) {
    names += NameId(id) -> string
  }

  // presumably all the loadClass calls happen at the start
  override def loadClass(classSerialNo: Int, classID: HeapId, stackTraceSerialNo: Int, classNameId: Long) {
    classNames += ClassId(classID) -> NameId(classNameId)
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
    val fields = fieldItems.map {fi =>
        val fieldName = names(NameId(fi.nameId))
        Field(fieldName, fi.itemType)
    }
    classes += ClassId(id) -> LazyClazz(classType, ClassId(superClassId), fields)
  }

  override def heapSummary(x: Int, totalLiveInstances: Int, xx: Long, totalInstancesAllocated: Long) {
    // this seems not to be called
    logger.info("instances should == " + totalLiveInstances + " with total of " + totalInstancesAllocated)
  }
}


trait ClassAwareDumpVisitor extends DumpVisitor {
  def classes: Map[ClassId, Clazz]

  final override def getClassFieldInfo(classHeapId: HeapId) =
    classes.get(ClassId(classHeapId)) map {c =>
      for {field <- c.fields} yield field.fieldType
    }
}

class RefVisitor(val classes: Map[ClassId, Clazz]) extends ClassAwareDumpVisitor with Logging {

  // HeapId => Ref
  val refs = debox.map.Map.empty[Long, Int]
  refs(0) = 0
  private def addRef(id: HeapId) {
    if (!refs.contains(id.id))
      refs(id.id) = refs.length
  }

  override def instanceDump(id: HeapId, x: Int, classId: HeapId, fields: Option[Vector[Any]], xx: Int) {
    addRef(id)
    fields.get foreach {
      _ match {
        case to: HeapId if !to.isNull =>
          addRef(to)
        case _ => // primitive
      }
    }
  }

  override def objectArrayDump(id: HeapId, x: Int, numElements: Int, classId: HeapId, elementIDs: Seq[HeapId]) {
    addRef(id)
    elementIDs foreach addRef
  }

  override def primitiveArray(id: HeapId, x: Int, fieldType: BaseFieldType, data: Seq[AnyVal]) {
    addRef(id)
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
    // TODO: create refs for statics/fields
//    if (staticItems.size > 0)
//      logger.info("seen " + staticItems.size + " static items")
//    if (fieldItems.size > 0)
//      logger.info("seen " + fieldItems.size + " field items")
  }

}


//class LinkingVisitor(refs: Int) extends DumpVisitorAlt with Logging {
//
//  val links = new CompressedColumns(refs)
//
//  val seen = debox.set.Set.empty[Int]
//  val known = debox.set.Set.empty[Int]
//
////  private val queue = new LinkedBlockingQueue[Any](Runtime.getRuntime.availableProcessors())
////  override def heapDumpEnd() {
////    while (queue.peek() != null) {
////      Thread.sleep(1000)
////    }
////  }
//
//  private def add(from: Ref, to: Ref) {
//    known add to.id
//    // parallel is actually slower
////    Future {
////      try {
//        links.set(from.id, to.id)
//        val tally = links.count
//        if (tally % 1000000 == 0)
//          logger.info(tally + " links")
////      } finally {
////        queue.poll()
////      }
////    } onFailure {
////      case t: Throwable => logger.error("", t)
////    }
//  }
//
//
//  override def instanceDump(id: Ref, classId: ClassId, fields: Vector[Any]) {
//    seen add id.id
//    known add id.id
//    (0 until fields.size) foreach {i =>
//        fields(i) match {
//          case to: Ref if !to.isNull =>
//            add(id, to)
//          case _ => // primitive
//        }
//    }
//  }
//
//  override def objectArrayDump(id: Ref, classId: ClassId, values: Seq[Ref]) {
//    seen add id.id
//    known add id.id
//    (0 until values.size) foreach { i =>
//      val to = values(i)
//      if (!to.isNull)
//        add(id, to)
//    }
//  }
//
//  override def primitiveArray(id: Ref, fieldType: BaseFieldType, data: Seq[AnyVal]) {
//    seen add id.id
//    known add id.id
//  }
//}
