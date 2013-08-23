package org.shelmet.heap.model

object FieldType {

  val types = Set[FieldType](ByteFieldType,BooleanFieldType,ShortFieldType,CharFieldType,IntFieldType,LongFieldType,
    FloatFieldType,DoubleFieldType,ObjectFieldType)

  val codeToTypeMapping = types.map( t => t.typeChar -> t).toMap

  def apply(fieldTypeChar : Char) = {
    codeToTypeMapping.getOrElse(fieldTypeChar,throw new IllegalArgumentException(s"field type '$fieldTypeChar' not recognized"))
  }
}

sealed abstract class FieldType(val typeChar : Char,val typeName : String) {
  val isObjectType = false
}

case object ByteFieldType extends FieldType('B',"byte")
case object BooleanFieldType extends FieldType('Z',"boolean")
case object ShortFieldType extends FieldType('S',"short")
case object CharFieldType extends FieldType('C',"char")
case object IntFieldType extends FieldType('I',"int")
case object LongFieldType extends FieldType('J',"long")
case object FloatFieldType extends FieldType('F',"float")
case object DoubleFieldType extends FieldType('D',"double")
case object ObjectFieldType extends FieldType('L',"object") {
  override val isObjectType = true
}

