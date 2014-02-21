//package org.shelmet.heap.parser
//
//import com.typesafe.scalalogging.slf4j.Logging
//import org.shelmet.heap.shared.{ObjectFieldType, BaseFieldType, FieldType, ClassType}
//import com.google.common.base.Stopwatch
//import debox.{Map => DMap}
//import java.io._
//import org.mapdb._
//import org.mapdb.Fun.Tuple2
//import org.shelmet.heap.HeapId
//import org.mapdb.BTreeKeySerializer.Tuple2KeySerializer
//import Serializer._
//import scala.concurrent.{ExecutionContext, Future}
//import java.util.concurrent.atomic.AtomicLong
//import scala.collection.JavaConverters._
//import java.util.concurrent.{Executors, Semaphore}
//
//object PersistantStructure extends App with Logging {
//
//  val file = new File("intellij.hprof")
//  implicit val lookup = HeapLookup(file)
//
//  println(Ref(31762658600L) references)
//  println(Ref(31762658600L) referencedBy)
//  println(Ref(31762658600L) typez)
//}
//
//class HeapLookup private(db: DB) extends Logging {
//
//  // TODO: other things we might care about, such as class counts
//
//  private val classesPersisted = db.getTreeMap[Long, Clazz]("classes")
//  private val refTypes = db.getTreeMap[Long, Long]("refTypes")
//  private val arrays = db.getTreeMap[Long, Arraz]("arrays")
//  private val linkedBy = db.getTreeSet[Tuple2[Long, Long]]("linkedBy")
//  private val linksTo = db.getTreeSet[Tuple2[Long, Long]]("linksTo")
//
//  // this is small enough, and accessed enough, to self-manage
//  private lazy val classes = DMap.empty[Long, Clazz] withEffect { dMap =>
//    classesPersisted.asScala foreach { case (k, v) =>
//      dMap(k) = v
//    }
//  }
//
//  def typez(ref: Ref): Typez = {
//    val classId = refTypes.get(ref.id)
//    classes.get(classId) match {
//      case Some(clazz) => clazz
//      case None => arrays.get(ref.id)
//    }
//  }
//
//  def referencedBy(ref: Ref): Set[Ref] = Fun.filter(linkedBy, ref.id).asScala.map { Ref(_) }.toSet
//
//  def references(ref: Ref): Set[Ref] = Fun.filter(linksTo, ref.id).asScala.map { Ref(_) }.toSet
//
//  // needs RAM loader
//  def values(ref: Ref): Seq[Either[AnyVal, Ref]] = ???
//
//  // needs random entry support in MapDB
//  def sample(): Ref = ???
//
//}
//
//object HeapLookup extends Logging {
//
//  def apply(heapDump: File): HeapLookup = {
//    val dbFile = new File(heapDump + ".idx")
//    val db = if (dbFile.isFile) {
//      val trial = DBMaker.newFileDB(dbFile).
//        mmapFileEnableIfSupported.
//        cacheSoftRefEnable.
//        readOnly.make()
//      if (isValidDb(trial)) trial
//      else {
//        trial.close()
//        deleteDb(dbFile)
//        createNewDb(dbFile, heapDump)
//      }
//    } else createNewDb(dbFile, heapDump)
//
//    new HeapLookup(db)
//  }
//
//  private def createNewDb(dbFile: File, heapDump: File) =
//    DBMaker.newFileDB(dbFile).
//      asyncWriteEnable.
//      fullChunkAllocationEnable.
//      closeOnJvmShutdown.
//      transactionDisable.
//      syncOnCommitDisable.
//      mmapFileEnableIfSupported.
//      cacheSoftRefEnable.
//      make() withEffect { db =>
//      new PersistingParser(db, heapDump).parse()
//    }
//
//  // this could probably be best achieved with reflection
//  private def isValidDb(db: DB): Boolean = {
//    List("classes", "arrays", "refTypes") forall { n =>
//      val expect = db.getAtomicLong(n + "Count").get.toInt
//      val seen = db.getTreeMap[Any, Any](n).size
//      logger.info(n + " " + expect + " " + seen)
//      expect > 0 && seen == expect
//    }
//  } && {
//    List("linkedBy", "linksTo") forall { n =>
//      val expect = db.getAtomicLong(n + "Count").get.toInt
//      val seen = db.getTreeSet[Any](n).size
//      logger.info(n + " " + expect + " " + seen)
//      expect > 0 && seen == expect
//    }
//  }
//
//  private def deleteDb(dbFile: File) = {
//    dbFile.delete()
//    new File(dbFile + ".p").delete()
//    new File(dbFile + ".t").delete()
//    Thread.sleep(500) // give the OS time to catch up
//  }
//
//}
//
//
//class PersistingParser(db: DB, heapDump: File) extends Logging {
//  def parse() {
//    val reader = new HprofReader(heapDump.toString)
//
//    def timed[T <: AnyRef](text: String)(f: => T): T = {
//      val watch = Stopwatch.createStarted()
//      f withEffect { r =>
//        logger.info(text + " took " + watch)
//      }
//    }
//
//    //    timed("reading full file") {
//    //      // for benchmarking
//    //      val reader = new BufferedInputStream(new FileInputStream(heapDump))
//    //      try {
//    //        val buffer = Array.ofDim[Byte](1024)
//    //        while (reader.read(buffer) != -1) {}
//    //        "done"
//    //      } finally reader.close()
//    //    }
//    //
//    //    timed("null reader") {
//    //      reader.readFile(new DumpVisitor {})
//    //      "done"
//    //    }
//
//    import ExecutionContext.Implicits.global
//
//    val classes = timed("class visitor") {
//      val visitor = new ClassVisitor(db)
//      reader.readFile(visitor)
//
//      db.createAtomicLong("refTypesCount", visitor.refTypesCount.get)
//      db.createAtomicLong("arraysCount", visitor.arraysCount.get)
//      db.commit()
//
//      visitor.lookup
//    }
//
//    Future { persist1(classes) }
//
//    timed("linking") {
//      val visitor = new LinkingVisitor(db, classes)
//      reader.readFile(visitor)
//      db.createAtomicLong("linksToCount", visitor.count.get)
//      db.createAtomicLong("linkedByCount", visitor.count.get)
//      db.commit()
//      "done"
//    }
//
//  }
//
//  private def persist1(classes: DMap[Long, Clazz]) {
//    db.createTreeMap("classes").counterEnable.valueSerializer(JAVA).makeLongMap[Clazz].withEffect { ts =>
//      classes foreach { (k, v) => ts.put(k, v) }
//      db.createAtomicLong("classesCount", classes.size)
//    }
//    db.commit()
//  }
//
//}
//
//case class Ref(id: Long) {
//  def isNull = id == 0
//
//  def references(implicit lookup: HeapLookup) = lookup.references(this)
//  def referencedBy(implicit lookup: HeapLookup) = lookup.referencedBy(this)
//  def typez(implicit lookup: HeapLookup) = lookup.typez(this)
//  def values(implicit lookup: HeapLookup) = lookup.values(this)
//}
//
//
//case class Field(name: String, fieldType: FieldType) {
//  def isRef = fieldType.isObjectType
//}
//
//sealed trait Typez
//
//case class Arraz(base: FieldType, length: Int) extends Typez
//
//case class Clazz(name: ClassType, fields: List[Field]) extends Typez {
//  def types = fields map {
//    _.fieldType
//  }
//
//  def fullFieldName(i: Int) = name + "." + fields(i).name
//}
//
//class ClassVisitor(db: DB) extends DumpVisitor with Logging with ThrottledFutureSupport {
//
//  val arrays = db.createTreeMap("arrays").counterEnable.valueSerializer(JAVA).makeLongMap[Arraz]
//  val arraysCount = new AtomicLong
//  val refTypes = db.createTreeMap("refTypes").counterEnable.valueSerializer(LONG).makeLongMap[Long]
//  val refTypesCount = new AtomicLong
//
//  private val classNames = DMap.empty[Long, Long]
//  private val names = DMap.empty[Long, String]
//  private val classes = DMap.empty[Long, LazyClazz]
//
//  // workaround https://github.com/non/debox/issues/2 NPE
//  def lookup = classes.mapValues(_ match {
//    case null => null
//    case o => o.toClazz(classes)
//  }
//  )
//
//  override def utf8String(id: Long, string: String) {
//    names += id -> string
//  }
//
//  // presumably all the loadClass calls happen at the start
//  override def loadClass(classSerialNo: Int, classID: HeapId, stackTraceSerialNo: Int, classNameId: Long) {
//    classNames(classID.id) = classNameId
//  }
//
//  case class LazyClazz(name: ClassType, superClass: HeapId, fields: List[Field]) {
//    def allFields(lookup: DMap[Long, LazyClazz]): List[Field] = fields ::: {
//      lookup.get(superClass.id) match {
//        case None => Nil
//        case Some(inherited) => inherited.allFields(lookup)
//      }
//    }
//
//    def toClazz(lookup: DMap[Long, LazyClazz]) = Clazz(name, allFields(lookup))
//  }
//
//  override def classDump(id: HeapId, stackTraceSerialId: Int,
//                         superClassId: HeapId,
//                         classLoaderId: HeapId,
//                         signerId: HeapId,
//                         protDomainId: HeapId,
//                         instanceSize: Int,
//                         constPoolEntries: Map[Int, Any],
//                         staticItems: List[ClassStaticEntry],
//                         fieldItems: List[ClassFieldEntry]) {
//    val classSig = names(classNames(id.id))
//    val classType = ClassType.parse(classSig)
//
//    val fields = fieldItems.map { fi =>
//      val fieldName = names(fi.nameId)
//      Field(fieldName, fi.itemType)
//    }
//    classes(id.id) = LazyClazz(classType, superClassId, fields)
//  }
//
//  override def instanceDump(id: HeapId, x: Int, classId: HeapId, fields: Option[Vector[Any]], xx: Int) {
//    refTypesCount.incrementAndGet()
//    ThrottledFuture {
//      refTypes.put(id.id, classId.id)
//    }
//  }
//
//  override def objectArrayDump(id: HeapId, x: Int, numElements: Int, classId: HeapId, elementIDs: Seq[HeapId]) {
//    arraysCount.incrementAndGet()
//    ThrottledFuture {
//      arrays.put(id.id, Arraz(ObjectFieldType, numElements))
//    }
//  }
//
//  override def primitiveArray(id: HeapId, x: Int, fieldType: BaseFieldType, data: Seq[AnyVal]) {
//    arraysCount.incrementAndGet()
//    ThrottledFuture {
//      arrays.put(id.id, Arraz(fieldType, data.length))
//    }
//  }
//}
//
//
//trait ClassAwareDumpVisitor extends DumpVisitor {
//  def classes: DMap[Long, Clazz]
//
//  final override def getClassFieldInfo(classHeapId: HeapId) =
//    classes.get(classHeapId.id) map { c =>
//      for {field <- c.fields} yield field.fieldType
//    }
//}
//
//
//trait ThrottledFutureSupport {
//  private val cpus = Runtime.getRuntime.availableProcessors
//  private val futures = new Semaphore(cpus * 2, true)
//
//  // context is specific to low cpu, high I/O scenarios
////  protected implicit val context = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(cpus * 4))
//  protected implicit val context = ExecutionContext.Implicits.global
//
//  // yuck, the taste of blocking.
//  // the correct way to do this is with parallel Observables
//  // that apply the throttling to the producer side and share
//  // threads between producer and consumer
//  protected def ThrottledFuture[T](block: => T) = {
//    // semaphores are ridiculously inefficient for this... orders of magnitude
//    // slower than single threaded.
//
////    futures.acquire()
////    Future {
////      futures.release()
//      block
////    }
//  }
//}
//
//
//class LinkingVisitor(db: DB, val classes: DMap[Long, Clazz]) extends ClassAwareDumpVisitor with Logging with ThrottledFutureSupport {
//
//  private val linkSerializer = new Tuple2KeySerializer[Long, Long](null, LONG, LONG)
//
//  val linkedBy = db.createTreeSet("linkedBy").counterEnable.serializer(linkSerializer).make[Tuple2[Long, Long]]
//  val linksTo = db.createTreeSet("linksTo").counterEnable.serializer(linkSerializer).make[Tuple2[Long, Long]]
//  val count = new AtomicLong
//
//  private def link(from: HeapId, tos: Set[HeapId]) {
//    assert(!from.isNull)
//    if (tos.isEmpty) return
//
//    count.getAndAdd(tos.size)
//    ThrottledFuture {
//      tos foreach { to =>
//        linkedBy.add(Fun.t2(to.id, from.id))
//        linksTo.add(Fun.t2(from.id, to.id))
//      }
//    }
//
//    val tally = count.get
//    if (tally % 1000000 == 0) {
//      logger.info(tally + " links")
//      db.commit()
//    }
//  }
//
//  override def instanceDump(id: HeapId, x: Int, classId: HeapId, fields: Option[Vector[Any]], xx: Int) {
//    val tos = fields.get.flatMap {
//      _ match {
//        case to: HeapId if !to.isNull => Some(to)
//        case _ => None
//      }
//    }.toSet
//    link(id, tos)
//  }
//
//  override def objectArrayDump(id: HeapId, x: Int, numElements: Int, classId: HeapId, elementIDs: Seq[HeapId]) {
//    val tos = elementIDs.filter { !_.isNull }.toSet
//    link(id, tos)
//  }
//}
