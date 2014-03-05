package org.shelmet.heap.model

import org.shelmet.heap.HeapId
import org.shelmet.heap.shared.ClassType

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
    if (name.contains("["))
      "<Arrays>"
    else {
      val pos = name.lastIndexOf(".")
      if (pos == -1)
        "<Default Package>"
      else
        name.substring(0, pos)
    }
  }

  final def getClazz: JavaClass = Snapshot.instance.getJavaLangClass

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
    }
  }

  def isString: Boolean = name == "java.lang.String"

  /**
   * Includes superclass fields
   */
  def getFieldsForInstance: List[JavaField] = fields ++ getSuperclass.map(_.getFieldsForInstance).getOrElse(List.empty)

  def getStatics: List[JavaStatic] = statics

  def getStaticField(name: String): Any = {
    statics.find(_.field.name == name) match {
      case Some(s) => s.getValue
      case None => null
    }
  }

  lazy val displayName : String = ClassType.parse(name).toString
  override def toString: String = "class " + displayName

  /**
   * @return true iff a variable of type this is assignable from an instance
   *         of other
   */
  def isAssignableFrom(other: JavaClass): Boolean = {
    assert(other != null)
    if (this eq other)
      true
    else
      other.getSuperclass.exists(this.isAssignableFrom)
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
}