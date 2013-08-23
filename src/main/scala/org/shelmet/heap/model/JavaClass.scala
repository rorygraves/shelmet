package org.shelmet.heap.model

import org.shelmet.heap.HeapId
import org.shelmet.heap.util.DisplayUtil

class JavaClass(snapshotV : Snapshot,
                heapId: HeapId,
                val name: String,
                val superClassId : HeapId,
                val loaderClassId : HeapId,
                val signerId : HeapId,
                val protDomainId : HeapId,
                statics: List[JavaStatic],
                instanceSize: Int,
                val fields : List[JavaField]
                ) extends JavaHeapObject(heapId,snapshotV) {

  def getPackage = {
    val pos = name.lastIndexOf(".")
    if (name.startsWith("["))
      "<Arrays>"
    else if (pos == -1)
      "<Default Package>"
    else
      name.substring(0, pos)
  }

  // all classes loaded by bootstrap loader are considered
  // platform classes.
  def isPlatformClass : Boolean = isBootstrap

  final def getClazz: JavaClass = snapshot.getJavaLangClass

  final def getSuperclass: Option[JavaClass] = superClassId.getOpt.asInstanceOf[Option[JavaClass]]

  final def loader: Option[JavaHeapObject] = loaderClassId.getOpt

  def getSigners: Option[JavaHeapObject] = signerId.getOpt

  def getProtectionDomain: Option[JavaHeapObject] = protDomainId.getOpt

  private var resolved = false

  override def resolve(snapshot: Snapshot) {
    if(!resolved) {
      resolved = true
      for (aStatic <- statics)
        aStatic.resolve(this, snapshot)

      snapshot.getJavaLangClass.addInstance(this)
    }

    getSuperclass.map( _.addSubClass(this))
  }

  private var instanceRefs : Set[HeapId] = Set.empty

  private var subclassRefs : Set[HeapId] = Set.empty

  def addSubClass(jc : JavaClass) {
    subclassRefs += jc.heapId
  }

  def isString: Boolean = name == "java.lang.String"

  /**
   * Includes superclass fields
   */
  def getFieldsForInstance: List[JavaField] = fields ++ getSuperclass.map(_.getFieldsForInstance).getOrElse(List.empty)

  def isArray: Boolean = name.indexOf('[') != -1

  def getInstances(includeSubclasses: Boolean): List[JavaHeapObject] = {
    val instances = instanceRefs.map(_.getOpt.get)

    if (includeSubclasses)
      instances.toList ++ getSubclasses.flatMap(_.getInstances(includeSubclasses = true))
    else
      instances.toList
  }

  /**
   * @return a count of the instances of this class
   */
  def getInstancesCount(includeSubclasses: Boolean): Int = {
    getInstances(includeSubclasses).size
  }

  def getSubclasses: Set[JavaClass] = subclassRefs.map(_.getOpt.get.asInstanceOf[JavaClass])

  /**
   * This can only safely be called after resolve()
   */
  def isBootstrap: Boolean = !loader.isDefined

  def getStatics: List[JavaStatic] = statics

  def getStaticField(name: String): Any = {
    statics.find(_.field.name == name) match {
      case Some(s) => s.getValue
      case None => null
    }
  }

  lazy val displayName : String = DisplayUtil.prettifyClassName(name)
  override def toString: String = "class " + displayName

  /**
   * @return true iff a variable of type this is assignable from an instance
   *         of other
   */
  def isAssignableFrom(other: JavaClass): Boolean = {
    if (this eq other) true
    else if (other == null) false
    else isAssignableFrom(other.getSuperclass.getOrElse(null))
  }

  /**
   * Describe the reference that this thing has to target.  This will only
   * be called if target is in the array returned by getChildrenForRootset.
   */
  override def describeReferenceTo(target: JavaHeapObject): List[String] = {

    var refs = statics.flatMap { aStatic =>
      val f = aStatic.field
      if(f.isObjectField && target == aStatic.getValue)
        List("static field " + f.name)
      else
        List.empty
    }

    if(target.heapId == superClassId)
      refs ::= "subclass"

    if(target.heapId == loaderClassId)
      refs ::= "classloader for"

    if(target.heapId == protDomainId)
      refs ::= "protection domain for"

    refs ::: super.describeReferenceTo(target)
  }

  /**
   * @return the size of an instance of this class.  Gives 0 for an array type.
   */
  def getInstanceSize: Int = instanceSize + snapshot.getMinimumObjectSize

  /**
   * @return The size of all instances of this class.  Correctly handles arrays.
   */
  lazy val getTotalInstanceSize: Long = {
    val instances = getInstances(includeSubclasses = false)
    val count: Int = instances.size
    if (count == 0 || !isArray)
      count * instanceSize
    else
      instances.foldLeft(0L)(_ + _.size)
  }

  override def size: Int = snapshot.getJavaLangClass.getInstanceSize

  override def visitReferencedObjects(visit : JavaHeapObject => Unit,includeStatics : Boolean = true) {
    super.visitReferencedObjects(visit,includeStatics)
    getSuperclass.foreach( visit(_) )
    loader.foreach( visit )
    getSigners.foreach( visit )
    getProtectionDomain.map( visit )

    if(includeStatics)
      for (aStatic <- statics) {
        val f = aStatic.field
        if (f.isObjectField) {
          val other = aStatic.getValue
          other match {
            case other1: JavaHeapObject => visit(other1)
            case _ =>
          }
        }
      }
  }

  private[model] def addInstance(inst: JavaHeapObject) {
    instanceRefs += inst.heapId
  }
}