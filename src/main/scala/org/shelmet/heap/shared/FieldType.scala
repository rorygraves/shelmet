package org.shelmet.heap.shared

object FieldType {
  val entries = Set[FieldType](BooleanFieldType,ByteFieldType,ShortFieldType,CharFieldType,IntFieldType,
      LongFieldType,FloatFieldType,DoubleFieldType,ObjectFieldType)

  val typeCharToType : Map[Char,FieldType] = entries.map(f => (f.jvmTypeChar,f)).toMap

  def fromJVMChar(jvmTypeChar : Char) : FieldType = {
    typeCharToType.get(jvmTypeChar).getOrElse(throw new IllegalArgumentException(s"JVM type char '$jvmTypeChar' not recognized"))
  }
}

sealed trait FieldType {
  def jvmTypeChar : Char
  def typeName : String
  def isObjectType: Boolean
}
// class representing base type fields (e.g int,short,boolean
sealed trait BaseFieldType extends FieldType {
  def fieldSize: Int
  override def isObjectType = false
}

// these are 64 bit UNIX field sizes
// and overly verbose to enable serialization
case object BooleanFieldType extends BaseFieldType {
  def jvmTypeChar = 'Z'
  def typeName = "boolean"
  def fieldSize = 4
}
case object ByteFieldType extends BaseFieldType {
  def jvmTypeChar = 'B'
  def typeName = "byte"
  def fieldSize = 2
}
case object ShortFieldType extends BaseFieldType {
  def jvmTypeChar = 'S'
  def typeName = "short"
  def fieldSize = 2
}
case object CharFieldType extends BaseFieldType {
  def jvmTypeChar = 'C'
  def typeName = "char"
  def fieldSize = 2
}
case object IntFieldType extends BaseFieldType {
  def jvmTypeChar = 'I'
  def typeName = "int"
  def fieldSize = 8
}
case object LongFieldType extends BaseFieldType {
  def jvmTypeChar = 'J'
  def typeName = "long"
  def fieldSize = 8
}
case object FloatFieldType extends BaseFieldType {
  def jvmTypeChar = 'F'
  def typeName = "float"
  def fieldSize = 4
}
case object DoubleFieldType extends BaseFieldType {
  def jvmTypeChar = 'D'
  def typeName = "double"
  def fieldSize = 8
}
case object ObjectFieldType extends FieldType {
  def jvmTypeChar = 'L'
  def typeName = "object"
  def isObjectType = true
}
