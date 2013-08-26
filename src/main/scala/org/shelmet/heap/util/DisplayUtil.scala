package org.shelmet.heap.util

import org.shelmet.heap.shared.{ObjectFieldType, FieldType}

object DisplayUtil {
  def prettifyClassName(sig : String) : String = {
    // classname examples
    // [[I
    // [Lcom.foo.Bar;
    // com.foo.Bar

    if(sig.contains('[')) {
      val brackets = sig.takeWhile(c=> c == '[')
      val typePart = sig.drop(brackets.size)
      val typeChar = typePart(0)
      val fieldType = FieldType.fromJVMChar(typeChar)
      val shortName = fieldType match {
        case ObjectFieldType => typePart.drop(1).dropRight(1)
        case x => x.typeName
      }
      println(s"sig = $sig. Shortname = $shortName  brackets=$brackets")
      shortName + brackets.map(x=> "[]").mkString
    } else
      sig
  }
}
