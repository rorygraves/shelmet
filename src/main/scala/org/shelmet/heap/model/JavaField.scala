package org.shelmet.heap.model

import org.shelmet.heap.shared.FieldType

class JavaField(val name: String,val longName : String,val fieldType : FieldType) {
  /**
   * @return true if the type of this field is heap object
   */
  def isObjectField: Boolean = fieldType.isObjectType
}