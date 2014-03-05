package org.shelmet.heap.model

import java.lang.ref.SoftReference
import scala.collection.SortedMap
import org.shelmet.heap.parser.HprofReader
import org.shelmet.heap.model.create.{ObjectPassDumpVisitor, InitialPassDumpVisitor}
import org.shelmet.heap.HeapId
import com.typesafe.scalalogging.slf4j.Logging
import org.shelmet.heap.shared.BaseFieldType

/**
 * Represents a snapshot of the Java objects in the VM at one instant.
 * This is the top-level "model" object read out of a single .hprof or .bod
 * file.
 */
object Snapshot extends Logging {
  val SMALL_ID_MASK: Long = 0x0FFFFFFFFL
  final val EMPTY_BYTE_ARRAY: Array[Byte] = new Array[Byte](0)

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
    snapshot.resolve()

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

  private var heapObjects = SortedMap[HeapId, JavaHeapObject]()
  var roots: Map[Int,Root] = Map()
  private var classes = SortedMap[String, JavaClass]()
  private var finalizablesCache: SoftReference[List[HeapId]] = null
  var weakReferenceClass: JavaClass = null
  var referentFieldIndex: Int = 0
  private var javaLangClass: JavaClass = null
  var javaLangObjectClass: JavaClass = null
  private var javaLangClassLoader: JavaClass = null
  var identifierSize: Int = 8

  def noObjects = heapObjects.size
  def noRoots = roots.size

  def setIdentifierSize(size: Int) {
    identifierSize = size
  }

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

  /**
   * Called after reading complete, to initialize the structure
   */
  def resolve() {
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