package org.shelmet.heap.model

import org.shelmet.heap.parser.DataReader
import org.shelmet.heap.HeapId

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

  def noFieldsOnClass : Int = fields.size

  lazy val totalNumFields: Int = getSuperclass match {
    case Some(sc) => sc.totalNumFields + noFieldsOnClass
    case None => noFieldsOnClass
  }

  def getPackage = {
    val pos = name.lastIndexOf(".")
    if (name.startsWith("["))
      "<Arrays>"
    else if (pos == -1)
      "<Default Package>"
    else
      name.substring(0, pos)
  }

  final def getClazz: JavaClass = snapshot.getJavaLangClass

  final def getSuperclass: Option[JavaClass] = superClassId.getOpt.asInstanceOf[Option[JavaClass]]

  final def getLoader: Option[JavaHeapObject] = loaderClassId.getOpt

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
   * Get a numbered field from this class
   */
  def getField(i: Int): JavaField = {
    val fields = this.fields
    if (i < 0 || i >= fields.length)
      throw new Error(s"No field $i for $displayName")
    fields(i)
  }

  /**
   * Get a numbered field from all the fields that are part of instance
   * of this class.  That is, include superclasses.
   */
  def getFieldForInstance(i: Int): JavaField = {
    getSuperclass match {
      case Some(sc) =>
        val superClassTotalFields = sc.totalNumFields
        if (i < superClassTotalFields)
          sc.getFieldForInstance(i)
        else
          getField(i - superClassTotalFields)
      case None =>
        getField(i)
    }
  }

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
  def isBootstrap: Boolean = !getLoader.isDefined

  /**
   * Includes superclass fields
   */
  def getFieldsForInstance: List[JavaField] = {

    val superItems = getSuperclass match {
        case Some(sc) => sc.getFieldsForInstance
        case None => List()
      }

    fields ++ superItems
  }

  def getStatics: List[JavaStatic] = statics

  def getStaticField(name: String): Any = {
    statics.find(_.field.name == name) match {
      case Some(s) => s.getValue
      case None => null
    }
  }

  def createDisplayName : String = {
    // -1 for none == 0, [ = 1 [[ = 2
    val lastBkt = name.lastIndexOf('[')
    val arrayBkts = lastBkt + 1

    if(lastBkt == -1)
      prettifyShortName(name)
    else {
      prettifyShortName(name.substring(lastBkt+1)) + (0 until arrayBkts).map(_ => "[]").mkString
    }
  }

  def prettifyShortName(shortName : String) : String =  {
    shortName.charAt(0) match {
      case 'B' => "byte"
      case 'Z' => "boolean"
      case 'S' => "short"
      case 'C' => "char"
      case 'I' => "int"
      case 'J' => "long"
      case 'F' => "float"
      case 'D' => "double"
      case 'L' => shortName.substring(1,shortName.length -1)
      case _ => shortName
    }

  }
  lazy val displayName : String = createDisplayName
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
//    for (aStatic <- statics) {
//      val f = aStatic.field
//      if (f.isObjectField) {
//        val other = aStatic.getValue
//        if (other == target) return
//      }
//    }

    if(target.heapId == superClassId)
      refs ::= "subclass"

    if(target.heapId == loaderClassId)
      refs ::= "classloader for"

    if(target.heapId == protDomainId)
      refs ::= "protection domain for"


    refs :::= super.describeReferenceTo(target)

    refs
  }

  /**
   * @return the size of an instance of this class.  Gives 0 for an array type.
   */
  def getInstanceSize: Int = {
    instanceSize + snapshot.getMinimumObjectSize
  }

  /**
   * @return The size of all instances of this class.  Correctly handles arrays.
   */
  def getTotalInstanceSize: Long = {
    val instances = getInstances(includeSubclasses = false)
    val count: Int = instances.size
    if (count == 0 || !isArray)
      return count * instanceSize

    instances.foldLeft(0L)(_ + _.size)
  }

  override def size: Int = snapshot.getJavaLangClass.getInstanceSize

  override def visitReferencedObjects(visit : JavaHeapObject => Unit) {
    super.visitReferencedObjects(visit)
    getSuperclass.foreach( visit(_) )
    getLoader.foreach( visit )
    getSigners.foreach( visit )
    getProtectionDomain.map( visit )

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

  def readAllFields(fieldReader : DataReader) : Vector[Any] = {
    val myFields = readClassFields(fieldReader)
    (myFields ++ getSuperclass.map( _.readAllFields(fieldReader)).getOrElse(Nil)).toVector
  }

  def readClassFields(fieldReader : DataReader) : List[Any] = {
    fields.map { f =>
      val sig = f.signature.charAt(0)

      sig match {
        case 'L' | '[' =>
          val id = fieldReader.readHeapId
          if(id.isNull)
            null
          else
          snapshot.findHeapObject(id).get
        case 'Z' => fieldReader.readBoolean
        case 'B' => fieldReader.readByte
        case 'S' => fieldReader.readShort
        case 'C' => fieldReader.readChar
        case 'I' => fieldReader.readInt
        case 'J' => fieldReader.readLong
        case 'F' => fieldReader.readFloat
        case 'D' => fieldReader.readDouble
        case _ =>
          throw new RuntimeException("invalid signature: " + sig)
      }
    }
  }


}