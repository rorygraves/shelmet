package org.shelmet.heap.model

case class JavaField(name: String,longName : String,signature: String) {

  /**
   * @return true if the type of this field is heap object
   */
  def isObjectField: Boolean = {
    val ch = signature.charAt(0)
    ch == '[' || ch == 'L'
  }
}