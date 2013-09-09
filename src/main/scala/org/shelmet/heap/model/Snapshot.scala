package org.shelmet.heap.model

import java.lang.ref.SoftReference
import scala.collection.{SortedSet, SortedMap}
import scala.collection.mutable.ListBuffer
import org.shelmet.heap.parser.HprofReader
import org.shelmet.heap.model.create.{ObjectPassDumpVisitor, InitialPassDumpVisitor}
import org.shelmet.heap.HeapId
import com.typesafe.scalalogging.slf4j.Logging
import java.util.Date
import org.shelmet.heap.shared.BaseFieldType
import scala.math.Ordering
import org.shelmet.heap.util.SortUtil

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
    logger.info("Calculating tree depths.")
    snapshot.calculateDepths()

    logger.info("Calculating retained sizes. (may take a while)")
    snapshot.calculateDominators()
    snapshot.calculateRetainedSizes()
    logger.info("Snapshot load complete.")
    snapshot
  }
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


  // TODO Need up update basic size calculations see:
  //    http://www.javamex.com/tutorials/memory/object_memory_usage.shtml
  //    http://www.javamex.com/tutorials/memory/array_memory_usage.shtml

  def calculateDominators() {
//    val startTime = System.currentTimeMillis()
//    val sortFn = SortUtil.sortByFn[JavaHeapObject](
//      (l,r)=>  r.minDepthToRoot - l.minDepthToRoot,
//      (l,r) => r.maxDepthToRoot - r.maxDepthToRoot,
//      (l,r) => (l.heapId.id - r.heapId.id).toInt
//    )
//
//    implicit val ord : Ordering[JavaHeapObject] = Ordering fromLessThan sortFn
//    var remaining = SortedSet[JavaHeapObject]() ++ heapObjects.values
//
//    println("BEFORE SIZE = " + remaining.size)
//    // first pass
//    remaining.foreach { obj =>
//      if(obj.dominator == UnknownDominator) {
//        val strongRefers = obj.referers.filterNot(_.refersOnlyWeaklyTo(obj))
////        println("SR = " + strongRefers +"  " +  obj.referers.size)
//        if(strongRefers.size < 2) {
//          if(strongRefers.size == 0)
//            obj.setDominator(NoDominotor)
//          else
//            obj.setDominator(HeapObjectDominator(strongRefers.head.heapId))
//          remaining -= obj
//        }
//      }
//    }
//
//    remaining.foreach { obj =>
//      if(obj.dominator == UnknownDominator) {
//        val newRefs = rootsetReferencesTo(obj, includeWeak = false)
//        if(!newRefs.isEmpty) {
//          val retainingSet = newRefs.map(_.objectSet).reduce(_.intersect(_))-obj
//          if(!retainingSet.isEmpty) {
//            val dominatorChain = newRefs.head.chain.filter(retainingSet.contains(_))
//            var cur = obj
//            var chain = dominatorChain.reverse
//            while(!chain.isEmpty) {
//              val head = chain.head
//              if(head.dominator != UnknownDominator)
//                chain = Nil else {
//                cur.setDominator(HeapObjectDominator(head.heapId))
//                cur = head
//                chain = chain.tail
//              }
//            }
//          }
//          else
//            obj.setDominator(NoDominotor)
//
//        } else
//          obj.setDominator(NoDominotor)
//      }
//
//    }
//
//    println("Adding totals")
//    // now go through and calculate retained
//    heapObjects.values foreach { obj =>
//      val size = obj.size
//      var dom = obj.dominator
//      var done = false
//      println("HERE - " + obj)
//      while(!done) {
//        dom match {
//          case nd : HeapObjectDominator =>
//            val nextObj = nd.heapId.get(this)
//            println("  nObj=" + nextObj)
//            nextObj.newRetained += size
//            dom = nextObj.dominator
//          case _ =>
//            done = true
//        }
//      }
//    }
//
//    val endTime = System.currentTimeMillis()
//    logger.info("Dominator chasing took " + (endTime - startTime) + "ms")
//
  }

  def calculateRetainedSizes() {
     val startTime = System.currentTimeMillis()
    var idx = 0
    var secondaryCount = 0

    var lastPercent = 0
    val total = heapObjects.size
    def incCount() {
      idx += 1
      val percent = (idx.toDouble / total * 100).floor.toInt
      if(percent > lastPercent) {
        lastPercent = percent
        if(percent % 10 == 0)
          print(percent)
        else
          print(".")
      }
    }

    // sorting furthest from root to nearest speads up calculation due to reuse of graphs
    heapObjects.values.toList.sortWith{
      case (a,b) =>
        val diff1 = (b.minDepthToRoot - a.minDepthToRoot)
        if(diff1 != 0)
          diff1 < 0
        else
          (b.maxDepthToRoot - a.maxDepthToRoot) > 0
    } foreach { obj =>
      if(!obj.retainedCalculated) {
        incCount()
        val size = obj.size
        val newRefs = rootsetReferencesTo(obj, includeWeak = false)
        if(!newRefs.isEmpty) {
          val retainingSet = newRefs.map(_.objectSet).reduce(_.intersect(_))-obj

          retainingSet.foreach( _.retaining += size )

          // try and use the graph for stuff closer to the root
          obj.retainedCalculated = true
          var targetObj = obj
          var targetRetained = retainingSet
          while(targetObj.referers.size == 1 && !targetObj.referers.head.retainedCalculated) {
            targetObj = targetObj.referers.head
            val targetSize = targetObj.size
            targetRetained -= targetObj
            incCount()
            secondaryCount += 1
            targetRetained.foreach( _.retaining += targetSize )
            targetObj.retainedCalculated = true
          }
        }
      }
    }
    val endTime = System.currentTimeMillis()
    println()
    println(s"Calculating retained size took ${endTime-startTime}ms")

  }

  def calculateDepths() {

    var visited = Set[HeapId]()

    var depth = 1
    var toVisit = Set[HeapId]()
    rootsMap.foreach { case (heapId,v) =>
      heapId.getOpt(this).get.addDepth(depth)
      toVisit += heapId
    }

    while(toVisit.size > 0) {
      depth += 1
      var nextToVisit = Set[HeapId]()
      toVisit.foreach { hId =>
        visited += hId
        hId.getOpt(this).foreach { obj =>
          obj.visitReferencedObjects({ r =>
            r.addDepth(depth)
            if(!visited.contains(r.heapId)) {
              nextToVisit += r.heapId
            }
          },false)
        }
      }
      toVisit = nextToVisit
    }


  }

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

    // TODO Determine if we actually want to do this - it hugely alters the counts of java.lang.Object
//    // resolve unknown heap objects
//    for (t <- heapObjects.values if t.isInstanceOf[UnknownHeapObject])
//      t.resolve(this)

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

    val fifo = ListBuffer[List[JavaHeapObject]]()
    var result = List[CompleteReferenceChain]()
    var visited = Set[HeapId](target.heapId)

    fifo += target :: Nil
    while (fifo.size > 0) {
      val chain: List[JavaHeapObject] = fifo.remove(0)
      val curr: JavaHeapObject = chain.head
      curr.getRootReferences.foreach { root =>
        result = new CompleteReferenceChain(root,chain) :: result
      }

      curr.referersSet.foreach {
        t =>
          if (!visited.contains(t)) {
            val obj = t.get(this)
            if ((includeWeak || !obj.refersOnlyWeaklyTo(curr))) {
              visited += t
              fifo += obj :: chain
            }
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