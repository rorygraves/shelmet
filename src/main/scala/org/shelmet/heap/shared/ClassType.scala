package org.shelmet.heap.shared

object ClassType {
  def parse(sig : String) : ClassType = {
    // classname examples
    // [[I
    // [Lcom.foo.Bar;
    // com.foo.Bar
    if (sig.contains("/"))
      parse(sig.replace("/", "."))
    else if (sig.startsWith("[")) {
      val subType = sig.drop(1)
      val containedTypeChar = subType.charAt(0)
      if(containedTypeChar == '[') {
        val containedType = parse(subType)
        new ObjectArrayClassType(containedType)
      } else {
        val fieldType = FieldType.fromJVMChar(containedTypeChar)
        fieldType match {
            // Lxxx.yyy.Z;  // drop the L and the ;
          case ObjectFieldType =>
            val cName = subType.drop(1).dropRight(1)
            new ObjectArrayClassType(new SimpleClassType(cName))
          case fieldType : BaseFieldType =>
            new BaseArrayClassType(fieldType)
        }
      }
    } else
      new SimpleClassType(sig)
  }
}

sealed trait ClassType

/**
 *
 * @tparam C the contained type
 */
sealed trait ArrayClassType[C] extends ClassType {
  def containedType : C
}

case class ObjectArrayClassType(containedType : ClassType) extends ArrayClassType[ClassType] {
  override def toString = containedType.toString + "[]"
}

case class BaseArrayClassType(containedType : BaseFieldType) extends ArrayClassType[FieldType] {
  override def toString = containedType.typeName + "[]"
}

case class SimpleClassType(classname : String) extends ClassType {
  override def toString = classname
}