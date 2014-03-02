package org.shelmet.heap.model

import java.lang.ref.SoftReference
import scala.collection.SortedMap
import scala.collection.mutable.ListBuffer
import org.shelmet.heap.parser.HprofReader
import org.shelmet.heap.model.create.{ObjectPassDumpVisitor, InitialPassDumpVisitor}
import org.shelmet.heap.HeapId
import com.typesafe.scalalogging.slf4j.Logging
import java.util.Date
import org.shelmet.heap.shared.BaseFieldType

/**
 * Represents a snapshot of the Java objects in the VM at one instant.
 * This is the top-level "model" object read out of a single .hprof or .bod
 * file.
 */
object Snapshot extends Logging {
  val SMALL_ID_MASK: Long = 0x0FFFFFFFFL
  final val EMPTY_BYTE_ARRAY: Array[Byte] = new Array[Byte](0)
  private val DOT_LIMIT: Int = 5000

  def readFromDump(hpr : HprofReader,callStack : Boolean,calculateRefs : Boolean) : Snapshot = {
    val snapshot = new Snapshot()
    Snapshot.setInstance(snapshot)
    logger.info("Resolving structure")
    val ipdv = new InitialPassDumpVisitor(snapshot,callStack)
    hpr.readFile(ipdv)
    logger.info(" found:")
    logger.info(s"   ${ipdv.classCount} class(es)")
    logger.info(s"   ${ipdv.objectCount} object(s)")
    logger.info("Resolving instances")
    hpr.readFile(new ObjectPassDumpVisitor(snapshot,callStack))
    logger.info("Parse step complete")
    logger.info("Snapshot read, resolving...")
    snapshot.resolve(calculateRefs)

    logger.info("Snapshot load complete.")
    Snapshot.clearInstance()
    snapshot
  }

  private val instanceHolder = new ThreadLocal[Snapshot]
  def instance : Snapshot = Option(instanceHolder.get()).getOrElse(throw new IllegalStateException("Instance not set for query"))
  def setInstance(snapshot : Snapshot) { instanceHolder.set(snapshot)}
  def clearInstance() { instanceHolder.set(null)}
}

class Snapshot extends Logging {

  import Snapshot._

  private var heapObjects = SortedMap[HeapId, JavaHeapObject]()
  var roots: Map[Int,Root] = Map()
  private var classes = SortedMap[String, JavaClass]()
  private var siteTraces = Map[HeapId, StackTrace]()
  private var rootsMap = Map[HeapId, Set[Root]]()
  private var finalizablesCache: SoftReference[List[HeapId]] = null
  var weakReferenceClass: JavaClass = null
  var referentFieldIndex: Int = 0
  private var javaLangClass: JavaClass = null
  var javaLangObjectClass: JavaClass = null
  private var javaLangClassLoader: JavaClass = null
  var identifierSize: Int = 8
  private var minimumObjectSize: Int = 0
  var creationDate : Option[Date] = None

  def setSiteTrace(obj: JavaHeapObject, trace: Option[StackTrace]) {
    trace match {
      case Some(t) if !t.frames.isEmpty =>
        siteTraces += (obj.heapId -> t)
      case _ =>
    }
  }

  def noObjects = heapObjects.size
  def noClasses = classes.size
  def noUserClasses = classes.values.filterNot(_.isPlatformClass).size
  def noRoots = roots.size

  def getSiteTrace(obj: JavaHeapObject): Option[StackTrace] = siteTraces.get(obj.heapId)

  def setIdentifierSize(size: Int) {
    identifierSize = size
    minimumObjectSize = 2 * size
  }

  def getMinimumObjectSize: Int = minimumObjectSize

  def addHeapObject(id: HeapId, ho: JavaHeapObject) {
    heapObjects += (id -> ho)
  }

  // TODO This is a horrible way to handle root ids - they should be assigned earlier
  def addRoot(r: Root) {
    r.index =roots.size
    roots += (r.index  -> r)
  }

  def addClass(id: HeapId, c: JavaClass) {
    addHeapObject(id, c)
    putInClassesMap(c)
  }

  def allObjects = heapObjects.values
  /**
   * Called after reading complete, to initialize the structure
   */
  def resolve(calculateRefs: Boolean) {
    logger.info("Resolving " + heapObjects.size + " objects...")

    javaLangObjectClass = findClassByName("java.lang.Object").getOrElse(throw new IllegalStateException("No java.lang.Object class"))

    javaLangClass = findClassByName("java.lang.Class").getOrElse(throw new IllegalStateException("No java.lang.Class class"))

    javaLangClassLoader = findClassByName("java.lang.ClassLoader").getOrElse(throw new IllegalStateException("No java.lang.ClassLoader class"))

    // resolve class objects
    for (t <- heapObjects.values if t.isInstanceOf[JavaClass])
        t.resolve(this)

    // resolve non-class objects
    for (t <- heapObjects.values if !t.isInstanceOf[JavaClass])
        t.resolve(this)

    weakReferenceClass = findClassByName("java.lang.ref.Reference").getOrElse(throw new IllegalStateException("No java.lang.ref.Reference"))
    referentFieldIndex = weakReferenceClass.getFieldsForInstance.indexWhere(_.name == "referent")

    if (calculateRefs)
      calculateReferencesToObjects()
  }

  private def calculateReferencesToObjects() {
    logger.info("Chasing references, expect " + heapObjects.size / DOT_LIMIT + " dots")
    var count: Int = 0
    for (t <- heapObjects.values) {
      t.visitReferencedObjects(_.addReferenceFrom(t))
      count += 1
      if (count % DOT_LIMIT == 0)
        logger.info(".")
    }

    for (r <- roots.values)
      findHeapObject(r.valueHeapId).foreach( _.addReferenceFromRoot(r))
  }

  def findHeapObject(heapId: HeapId): Option[JavaHeapObject] = findThing(heapId)

  def findThing(id: HeapId,createIfMissing : Boolean =true): Option[JavaHeapObject] = {
    if(id.isNull)
      None
    else {
      val res = heapObjects.get(id)
      res match {
        case Some(_) =>
          res
        case None =>
          if(createIfMissing) {
            val obj = new UnknownHeapObject(id,this)
            heapObjects += (id -> obj)
            Some(obj)
          } else
            None
      }
    }
  }

  def findClassByName(name : String) : Option[JavaClass] = {
    classes.get(name)
  }

  /**
   * Return an Iterator of all of the classes in this snapshot.
   **/
  def getClasses: Iterable[JavaClass] = classes.values

  def getFinalizerObjects: List[HeapId] = {
    if (finalizablesCache != null) {
      val obj: List[HeapId] = finalizablesCache.get
      if (obj != null) return obj
    }
    val clazz: JavaClass = findClassByName("java.lang.ref.Finalizer").getOrElse(throw new IllegalStateException("Cant find Finalizer class"))
    val queue: JavaObject = clazz.getStaticField("queue").asInstanceOf[JavaObject]
    val tmp: Any = queue.getField("head")
    var finalizablesC: List[HeapId] = Nil
    if (tmp != null) {
      var head: JavaObject = tmp.asInstanceOf[JavaObject]
      var done = false
      while (!done) {
        val referent: JavaHeapObject = head.getField("referent").asInstanceOf[JavaHeapObject]
        val next: Any = head.getField("next")
        if (next == null ||  next == head) {
          done = true
        } else {
          head = next.asInstanceOf[JavaObject]
          finalizablesC ::= referent.heapId
        }
      }
    }
    finalizablesCache = new SoftReference[List[HeapId]](finalizablesC.reverse)
    finalizablesC
  }

  def rootsetReferencesTo(target: JavaHeapObject, includeWeak: Boolean): List[CompleteReferenceChain] = {

    val fifo = ListBuffer[List[HeapId]]()
    var result = List[CompleteReferenceChain]()
    var visited = Set[HeapId](target.heapId)

    fifo += target.heapId :: Nil
    while (fifo.size > 0) {
      val chain: List[HeapId] = fifo.remove(0)
      val curr: JavaHeapObject = chain.head.get
      curr.getRootReferences.foreach { root =>
        result = new CompleteReferenceChain(root,chain) :: result
      }

      val set = if(includeWeak)
        curr.referersSet
      else
        curr.hardRefersSet
      set.foreach {
        t =>
          if (!visited.contains(t)) {
            visited += t
            fifo += t :: chain
          }
      }
    }
    result
  }

  private[model] def addReferenceFromRoot(root: Root, obj: JavaHeapObject) {
    rootsMap += obj.heapId -> (rootsMap.getOrElse(obj.heapId,Set.empty) + root)
  }

  private[model] def getRoots(obj: JavaHeapObject): Set[Root] = rootsMap.getOrElse(obj.heapId,Set.empty)

  private[model] def getJavaLangClass: JavaClass = javaLangClass

  private[model] def getPrimitiveArrayClass(elementType : BaseFieldType): JavaClass = {

    val className= elementType.typeName + "[]"
    findClassByName(className).getOrElse(throw new IllegalStateException("Unable to resolve class " + className))
  }

  private def putInClassesMap(c: JavaClass) {
    var name: String = c.name
    if (classes.contains(name))
      name += "-" + c.getIdString

    classes += (c.name -> c)
  }
}