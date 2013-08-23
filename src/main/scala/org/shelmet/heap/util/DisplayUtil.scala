package org.shelmet.heap.util

object DisplayUtil {
  def prettifyClassName(sig : String) : String = {
    // -1 for none == 0, [ = 1 [[ = 2
    val lastBkt = sig.lastIndexOf('[')
    val arrayBkts = lastBkt + 1

    if(lastBkt == -1)
      prettifyShortName(sig)
    else {
      prettifyShortName(sig.substring(lastBkt+1)) + (0 until arrayBkts).map(_ => "[]").mkString
    }
  }

  def prettifyShortName(sig : String) : String =  {
    sig.charAt(0) match {
      case 'B' => "byte"
      case 'Z' => "boolean"
      case 'S' => "short"
      case 'C' => "char"
      case 'I' => "int"
      case 'J' => "long"
      case 'F' => "float"
      case 'D' => "double"
      // TODO This is definately wrong - any class name with an L at the start (we get unencoded names atm)
      case 'L' => sig.drop(1).dropRight(1)
      case _ => sig //throw new IllegalStateException("shortName")
    }
  }
}
