package org.shelmet.heap.model

class JavaField(val name: String,val longName : String,val signature: String) {

  /**
   * @return true if the type of this field is heap object
   */
  def isObjectField: Boolean = {
    val ch = signature.charAt(0)
    ch == '[' || ch == 'L'
  }
}