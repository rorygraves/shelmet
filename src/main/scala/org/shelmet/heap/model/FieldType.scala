package org.shelmet.heap.model

sealed abstract class FieldType(val typeName : String) {
  val isObjectType = false
}

case object ByteFieldType extends FieldType("byte")
case object BooleanFieldType extends FieldType("boolean")
case object ShortFieldType extends FieldType("short")
case object CharFieldType extends FieldType("char")
case object IntFieldType extends FieldType("int")
case object LongFieldType extends FieldType("long")
case object FloatFieldType extends FieldType("float")
case object DoubleFieldType extends FieldType("double")
case object ObjectFieldType extends FieldType("object") {
  override val isObjectType = true
}

