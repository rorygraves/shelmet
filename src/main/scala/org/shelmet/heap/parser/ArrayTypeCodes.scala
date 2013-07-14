package org.shelmet.heap.parser

/**
 * Primitive array type codes as defined by VM specification.
 */
object ArrayTypeCodes {
  // Typecodes for array elements.
  // Refer to newarray instruction in VM Spec.
  final val T_BOOLEAN: Int = 4
  final val T_CHAR: Int = 5
  final val T_FLOAT: Int = 6
  final val T_DOUBLE: Int = 7
  final val T_BYTE: Int = 8
  final val T_SHORT: Int = 9
  final val T_INT: Int = 10
  final val T_LONG: Int = 11
}