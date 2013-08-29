package org.shelmet.heap.shared

object FieldType {
  val entries = Set[FieldType](BooleanFieldType,ByteFieldType,ShortFieldType,CharFieldType,IntFieldType,
      LongFieldType,FloatFieldType,DoubleFieldType,ObjectFieldType)

  val typeCharToType : Map[Char,FieldType] = entries.map(f => (f.jvmTypeChar,f)).toMap

  def fromJVMChar(jvmTypeChar : Char) : FieldType = {
    typeCharToType.get(jvmTypeChar).getOrElse(throw new IllegalArgumentException(s"JVM type char '$jvmTypeChar' not recognized"))
  }
}

sealed abstract class FieldType(val jvmTypeChar : Char,val typeName : String) {
  val isObjectType = false
}
// class representing base type fields (e.g int,short,boolean
sealed abstract class BaseFieldType(jvmTypeChar : Char,typeName : String,val fieldSize : Int) extends FieldType(jvmTypeChar,typeName)

case object BooleanFieldType extends BaseFieldType('Z',"boolean",1)
case object ByteFieldType extends BaseFieldType('B',"byte",1)
case object ShortFieldType extends BaseFieldType('S',"short",2)
case object CharFieldType extends BaseFieldType('C',"char",2)
case object IntFieldType extends BaseFieldType('I',"int",4)
case object LongFieldType extends BaseFieldType('J',"long",8)
case object FloatFieldType extends BaseFieldType('F',"float",4)
case object DoubleFieldType extends BaseFieldType('D',"double",8)
case object ObjectFieldType extends FieldType('L',"object") {
  override val isObjectType = true
}

