package org.shelmet.heap.model

import java.lang.ref.SoftReference
import scala.collection.SortedMap
import scala.collection.mutable.ListBuffer
import org.shelmet.heap.parser.HprofReader
import org.shelmet.heap.model.create.{ObjectPassDumpVisitor, InitialPassDumpVisitor}
import org.shelmet.heap.HeapId
import com.typesafe.scalalogging.slf4j.Logging
import java.util.Date
import org.shelmet.heap.util.PlatformClasses

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
    logger.info("Resolving structure")
    hpr.readFile(new InitialPassDumpVisitor(snapshot,callStack))
    logger.info("Resolving instances")
    hpr.readFile(new ObjectPassDumpVisitor(snapshot,callStack))
    logger.info("Parse step complete")
    logger.info("Snapshot read, resolving...")
    snapshot.resolve(calculateRefs)
    logger.info("Snapshot resolved.")
    snapshot
  }
}

class Snapshot extends Logging {

  import Snapshot._

  private var heapObjects = Map[HeapId, JavaHeapObject]()
  var roots: List[Root] = Nil
  private var classes = SortedMap[String, JavaClass]()
  private var siteTraces = Map[JavaHeapObject, StackTrace]()
  private var rootsMap = Map[JavaHeapObject, Root]()
  private var finalizablesCache: SoftReference[List[JavaHeapObject]] = null
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
        siteTraces += (obj -> t)
      case _ =>
    }
  }

  def noObjects = heapObjects.size
  def noClasses = classes.size
  def noUserClasses = classes.values.filterNot(PlatformClasses.isPlatformClass).size
  def noRoots = roots.size

  def getSiteTrace(obj: JavaHeapObject): Option[StackTrace] = siteTraces.get(obj)



  def setIdentifierSize(size: Int) {
    identifierSize = size
    minimumObjectSize = 2 * size
  }

  def getMinimumObjectSize: Int = minimumObjectSize

  def addHeapObject(id: HeapId, ho: JavaHeapObject) {
    heapObjects += (id -> ho)
  }

  def addRoot(r: Root) {
    r.index =roots.size
    roots ++= List(r)
  }

  def addClass(id: HeapId, c: JavaClass) {
    addHeapObject(id, c)
    putInClassesMap(c)
  }

  /**
   * Called after reading complete, to initialize the structure
   */
  def resolve(calculateRefs: Boolean) {
    logger.info("Resolving " + heapObjects.size + " objects...")

    javaLangObjectClass = findClassByName("java.lang.Object").getOrElse(throw new IllegalStateException("No java.lang.Object class"))

    javaLangClass = findClassByName("java.lang.Class").getOrElse(throw new IllegalStateException("No java.lang.Class class"))

    javaLangClassLoader = findClassByName("java.lang.ClassLoader").getOrElse(throw new IllegalStateException("No java.lang.ClassLoader class"))

    for (t <- heapObjects.values)
      if (t.isInstanceOf[JavaClass])
        t.resolve(this)

    for (t <- heapObjects.values
      if !t.isInstanceOf[JavaClass])
        t.resolve(this)

    weakReferenceClass = findClassByName("java.lang.ref.Reference").getOrElse(throw new IllegalStateException("No java.lang.ref.Reference"))
    referentFieldIndex = weakReferenceClass.getFieldsForInstance.indexWhere(_.name == "referent")

    if (calculateRefs) {
      calculateReferencesToObjects()
      logger.info("Eliminating duplicate references")
      var count: Int = 0
      for (t <- heapObjects.values) {
        count += 1
        if (calculateRefs && count % DOT_LIMIT == 0) {
          logger.info(".")
        }
      }
    }
  }

  private def calculateReferencesToObjects() {
    logger.info("Chasing references, expect " + heapObjects.size / DOT_LIMIT + " dots")
    var count: Int = 0
    for (t <- heapObjects.values) {
      t.visitReferencedObjects( _.addReferenceFrom(t))
      count += 1
      if (count % DOT_LIMIT == 0)
        logger.info(".")
    }

    for (r <- roots)
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

  def getFinalizerObjects: List[JavaHeapObject] = {
    if (finalizablesCache != null) {
      val obj: List[JavaHeapObject] = finalizablesCache.get
      if (obj != null) return obj
    }
    val clazz: JavaClass = findClassByName("java.lang.ref.Finalizer").getOrElse(throw new IllegalStateException("Cant find Finalizer class"))
    val queue: JavaObject = clazz.getStaticField("queue").asInstanceOf[JavaObject]
    val tmp: Any = queue.getField("head")
    var finalizablesC: List[JavaHeapObject] = Nil
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
          finalizablesC ::= referent
        }
      }
    }
    finalizablesCache = new SoftReference[List[JavaHeapObject]](finalizablesC.reverse)
    finalizablesC
  }

  def rootsetReferencesTo(target: JavaHeapObject, includeWeak: Boolean): List[ReferenceChain] = {
    val fifo = ListBuffer[ReferenceChain]()
    var visited = Set[JavaHeapObject]()
    var result = List[ReferenceChain]()
    visited += target

    fifo += new ReferenceChain(target, null)
    while (fifo.size > 0) {
      val chain: ReferenceChain = fifo.remove(0)
      val curr: JavaHeapObject = chain.obj
      if (curr.getRoot != null)
        result = chain ::result

      curr.referers.foreach {
        t =>
          if (!visited.contains(t)) {
            if (includeWeak || !t.refersOnlyWeaklyTo(this, curr)) {
              visited += t
              fifo += new ReferenceChain(t, chain)
            }
          }
      }
    }
    result
  }

  private[model] def addReferenceFromRoot(r: Root, obj: JavaHeapObject) {
    rootsMap.get(obj) match {
      case Some(root) => root.mostInteresting(r)
      case None => rootsMap += (obj -> r)
    }
  }

  private[model] def getRoot(obj: JavaHeapObject): Root = rootsMap.getOrElse(obj,null)

  private[model] def getJavaLangClass: JavaClass = javaLangClass

  private[model] def getArrayClass(elementSignature: String): JavaClass = {
    val className= "[" + elementSignature
    findClassByName(className).getOrElse(throw new IllegalStateException("Unable to resolve class " + className))
  }

  private def putInClassesMap(c: JavaClass) {
    var name: String = c.name
    if (classes.contains(name))
      name += "-" + c.getIdString

    classes += (c.name -> c)
  }
}