package org.shelmet.heap.parser

import com.typesafe.scalalogging.slf4j.Logging
import scala.collection.mutable
import org.shelmet.heap.shared.{FieldType, ClassType}
import org.shelmet.heap.HeapId
import com.google.common.base.Stopwatch
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}
import java.io.File


object SparseLinkStructure extends App with Logging {

  val reader = new HprofReaderAlt(new File("intellij.hprof"))
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
  logger.info("COUNTING pass took " + watch + " with " + reader.refs.length)

  watch.reset()
  watch.start()
  val linkingVisitor = new LinkingVisitor(countingVisitor.lookup, reader.refs.length)
  reader.readFile(linkingVisitor)

  val links = linkingVisitor.links
  logger.info("LINKING pass took " + watch + " with " + reader.refs.length)

  logger.info(s"${links.columns } refs with ${links.count } links")

//  System.gc()
//  System.gc()
//  System.gc()
//  System.gc()
//  System.gc()
//  Thread.sleep(30000)
//
//  logger.info(s"${links.columns } refs with ${links.count } links")

}

class CountingVisitor extends DumpVisitorAlt with Logging {
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

class LinkingVisitor(classes: Map[ClassId, Clazz], refs: Int) extends DumpVisitorAlt with Logging {

  val links = new CompressedColumns(refs)

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

  override def getFieldTypes(classHeapId: ClassId): Option[List[FieldType]] =
    classes.get(classHeapId) map { _.types }

  override def instanceDump(id: Ref, classId: ClassId, fields: Vector[Any]) {
    (0 until fields.size) foreach {i =>
        fields(i) match {
          case to: Ref if !to.isNull =>
            add(id, to)
          case _ => // primitive
        }
    }
  }

  override def objectArrayDump(id: Ref, classId: ClassId, values: Seq[Ref]) {
    (0 until values.size) foreach { i =>
      val to = values(i)
      if (!to.isNull)
        add(id, to)
    }
  }


}
