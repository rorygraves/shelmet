package org.shelmet.heap.parser

import com.typesafe.scalalogging.slf4j.Logging
import scala.collection.mutable
import org.shelmet.heap.shared.{BaseFieldType, FieldType, ClassType}
import org.shelmet.heap.HeapId
import com.google.common.base.Stopwatch
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}
import java.io.File


// for persist to disc
// https://github.com/jankotek/MapDB/blob/master/src/test/java/examples/MultiMap.java

object SparseLinkStructure extends App with Logging {

 val reader = new HprofReaderAlt(new File("intellij.hprof"))
//    val reader = new HprofReaderAlt(new File("heap.bin"))
//    val reader = new HprofReaderAlt(new File("sbt.hprof"))
//  val reader = new HprofReaderAlt(new File("visualvm.hprof"))


  val watch = Stopwatch.createStarted()
  reader.readFile(new DumpVisitorAlt {})
  logger.info("initial passes took " + watch)

  watch.reset()
  watch.start()
  val linkingVisitor = new LinkingVisitor(reader.refs.length)
  reader.readFile(linkingVisitor)

  val links = linkingVisitor.links
  logger.info("LINKING pass took " + watch + " with " + reader.refs.length)

  logger.info(s"${links.columnSize } refs with ${links.count } links")

  logger.info("dumps for " + linkingVisitor.seen.length + " but " + linkingVisitor.known.length + " refs in the link structure")

  reader.db.close()

//  System.gc()
//  System.gc()
//  System.gc()
//  System.gc()
//  System.gc()
//  Thread.sleep(30000)
//
//  logger.info(s"${links.columnSize } refs with ${links.count } links")

}

class LinkingVisitor(refs: Int) extends DumpVisitorAlt with Logging {

  val links = new CompressedColumns(refs)

  val seen = debox.set.Set.empty[Int]
  val known = debox.set.Set.empty[Int]

//  private val queue = new LinkedBlockingQueue[Any](Runtime.getRuntime.availableProcessors())
//  override def heapDumpEnd() {
//    while (queue.peek() != null) {
//      Thread.sleep(1000)
//    }
//  }

  private def add(from: Ref, to: Ref) {
    known add to.id
    // parallel is actually slower
//    Future {
//      try {
        links.set(from.id, to.id)
        val tally = links.count
        if (tally % 1000000 == 0)
          logger.info(tally + " links")
//      } finally {
//        queue.poll()
//      }
//    } onFailure {
//      case t: Throwable => logger.error("", t)
//    }
  }


  override def instanceDump(id: Ref, classId: ClassId, fields: Vector[Any]) {
    seen add id.id
    known add id.id
    (0 until fields.size) foreach {i =>
        fields(i) match {
          case to: Ref if !to.isNull =>
            add(id, to)
          case _ => // primitive
        }
    }
  }

  override def objectArrayDump(id: Ref, classId: ClassId, values: Seq[Ref]) {
    seen add id.id
    known add id.id
    (0 until values.size) foreach { i =>
      val to = values(i)
      if (!to.isNull)
        add(id, to)
    }
  }

  override def primitiveArray(id: Ref, fieldType: BaseFieldType, data: Seq[AnyVal]) {
    seen add id.id
    known add id.id
  }
}
