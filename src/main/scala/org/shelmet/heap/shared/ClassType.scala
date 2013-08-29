package org.shelmet.heap.shared

object ClassType {
  def parse(sig : String) : ClassType = {
    // classname examples
    // [[I
    // [Lcom.foo.Bar;
    // com.foo.Bar

    if(sig.startsWith("[")) {
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

abstract sealed class ClassType() {
  override def toString : String
}

/**
 *
 * @tparam C the contained type
 */
abstract class ArrayClassType[C] extends ClassType {

  def containedType : C
}

class ObjectArrayClassType(val containedType : ClassType) extends ArrayClassType[ClassType] {
  override def toString = containedType.toString + "[]"
}

class BaseArrayClassType(val containedType : BaseFieldType) extends ArrayClassType[FieldType] {
  override def toString = containedType.typeName + "[]"
}

class SimpleClassType(classname : String) extends ClassType {
  override def toString = classname
}