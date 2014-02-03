package org.shelmet.heap.model

import org.shelmet.heap.shared.FieldType

case class JavaField(name: String,longName : String,fieldType : FieldType) extends Serializable {
  /**
   * @return true if the type of this field is heap object
   */
  def isObjectField: Boolean = fieldType.isObjectType
}