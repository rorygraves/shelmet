package org.shelmet.heap.server

import org.shelmet.heap.model._
import org.shelmet.heap.util.Misc

class ObjectPage(snapshot : Snapshot,query : String) extends AbstractPage(snapshot) {

  override def run() {
    findObjectByQuery(query) match {
      case None =>
        html("Missing object " + query) {
          out.println("object not found")
        }
      case Some(thing : JavaObject) =>
        html(s"instance of ${thing.getClazz.name} @ ${thing.heapId.toHex}") {
          printFullObject(thing)
          printAllocationSite(thing)
          printReferencesTo(thing)
        }
      case Some(clazz : JavaClass) =>
        val name = s"class ${clazz.createDisplayName} (${clazz.heapId.toHex})"
        html(name) {
          printFullClass(clazz)
        }
      case Some(thing : JavaValueArray) =>
        html("Object at " + query) {
          printEncoded(thing.valueString(bigLimit = true))
          printAllocationSite(thing)
          printReferencesTo(thing)
        }
      case Some(thing : JavaObjectArray) =>
        html("Object at " + query) {
          printFullObjectArray(thing)
          printAllocationSite(thing)
          printReferencesTo(thing)
        }
      case Some(thing : JavaHeapObject) =>
        html("Object at " + query) {
          printEncoded(thing.toString)
          printReferencesTo(thing)
        }
    }
  }

  private def printFullObject(obj: JavaObject) {
    out.print(" ")

    table {
      tableRow {
        tableData(out.println("<b>Instance of:</b>"))
        tableData(printClass(obj.getClazz))
      }
      tableRow {
        tableData(out.println("<b>Size</b>"))
        tableData(out.println(s"""${obj.size} bytes"""))
      }
    }
    // special string handling
    obj match {
      case jso : JavaObject if jso.getClazz.isString =>

        val value: Any = jso.getField("value")
        val valStr = "<h2>Value:</h2> " + Misc.encodeHtml(value match {
          case array: JavaValueArray => array.valueString()
          case _ => "null"
        })

        out.println(valStr)
      case _ =>
    }

    out.println("<h2>Instance data members:</h2>")
    val fields: List[JavaField] = obj.getClazz.getFieldsForInstance

    val things: Vector[Any] = obj.getFields

    val set = fields.zip(things).sortWith {
      case ((lhsField,lhsThing),(rhsField,rhsThing)) =>
        lhsField.longName.compareTo(rhsField.longName) < 0
    }

    set foreach {
      case (field,thing) =>
        printField(field)
        out.print(" : ")
        printThing(thing)
        out.println("<br/>")
    }
  }

  private def printFullObjectArray(arr: JavaObjectArray) {
    val elements = arr.elements.zipWithIndex
    out.println("<h1>Array of " + elements.size + " objects</h1>")
    out.println("<h2>Class:</h2>")
    printClass(arr.getClazz)
    out.println("<h2>Values</h2>")
    elements foreach { case (item,idx) =>
      out.print("" + idx + " : ")
      printThing(item)
      out.println("<br/>")
    }
  }

  private def printAllocationSite(obj: JavaHeapObject) {
    obj.getAllocatedFrom match {
      case Some(trace) if !trace.frames.isEmpty =>
        out.println("<h2>Object allocated from:</h2>")
        printStackTrace(trace)
      case _ =>
    }
  }


  protected def printFullClass(clazz: JavaClass) {

    val subClasses = clazz.getSubclasses
    val subclassCount = subClasses.size

    val instanceFields = clazz.fields.sortWith((jf1,jf2) => jf1.name.compareTo(jf2.name) < 0)
    val instanceFieldsCount = instanceFields.size

    val staticFields = clazz.getStatics
    val staticFieldsCount = staticFields.size

    table {
      tableRow {
        tableData { out.println("<b>Superclass:</b>") }
        tableData { printClass(clazz.getSuperclass.getOrElse(null)) }
      }
      tableRow {
        tableData { out.println("<b>ClassLoader:</b>") }
        tableData { printThing(clazz.getLoader.getOrElse(null)) }
      }
      tableRow {
        tableData { out.println("<b>Signers:</b>") }
        tableData { printThing(clazz.getSigners.getOrElse(null)) }
      }
      tableRow {
        tableData { out.println("<b>Protection Domain:</b>") }
        tableData { printThing(clazz.getProtectionDomain.getOrElse(null)) }
      }
      tableRow {
        tableData { out.println("<b>Subclasses</b>") }
        tableData {
          if(subclassCount == 0)
            out.println("None")
          else
            out.println(s"""<a href="#subclasses">$subclassCount</a>""")}
      }
      tableRow {
        tableData { out.println("<b>Instance Fields</b>") }
        tableData {
          if(instanceFieldsCount == 0)
            out.println("None")
          else
            out.println(s"""<a href="#instanceFields">$instanceFieldsCount</a>""")}
      }
      tableRow {
        tableData { out.println("<b>Static Fields</b>") }
        tableData {
          if(staticFieldsCount == 0)
            out.println("None")
          else
            out.println(s"""<a href="#staticFields">$staticFieldsCount</a>""")}
      }
    }

    def ul[A](values : Iterable[A],f : A=>Unit) {
      out.println("<ul>")
      values foreach { a =>
        out.println("<li>")
        f(a)
        out.println("</li>")
      }

      out.println("</ul>")
    }

    if(subclassCount > 0) {
      pageAnchor("subclasses")
      h2("Subclasses:")
      ul(subClasses,printClass)
    }

    if(instanceFieldsCount > 0) {
      pageAnchor("instanceFields")
      h2("Instance Fields:")
      ul(instanceFields,printField)
    }

    if(staticFieldsCount > 0) {
      pageAnchor("staticFields")
      h2("Static Fields:")
      ul(staticFields,printStatic)
    }

    val instanceCountWithSub = clazz.getInstancesCount(includeSubclasses = true)

    h2(s"Instances ($instanceCountWithSub)")
    if(instanceCountWithSub != 0) {

      if(subClasses.isEmpty)
        printAnchor(s"instances/${encodeForURL(clazz)}",s"of this class (${clazz.getInstancesCount(includeSubclasses = false)})")
      else {
        printAnchor(s"instances/${encodeForURL(clazz)}",s"of this class (${clazz.getInstancesCount(includeSubclasses = false)})")
        out.println("<br/>")
        printAnchor("allInstances/" + encodeForURL(clazz),s"of this class with subclasses ($instanceCountWithSub)")
      }
    }

    out.println("<br/>")
    h2("References summary by Type")
    printAnchor("refsByType/" + encodeForURL(clazz),"References summary by type")
    printReferencesTo(clazz)
  }

  protected def printReferencesTo(obj: JavaHeapObject) {
    if (obj.heapId.id == -1) {
      return
    }
    h2("References to this object:")
    obj.referers.toList.sortWith((a, b) => a.toString.compareTo(b.toString) <0) foreach {
      ref =>
        printThing(ref)
        printEncoded(" : " + ref.describeReferenceTo(obj).mkString("/"))
        out.println("<br/>")
    }
    h2("Other Queries")
    out.println("Reference Chains from Rootset")
    val id: Long = obj.heapId.id
    out.println("<ul>")
    out.println("<li>")
    printAnchor("objectRootsExcWeak/" + hexString(id),"Exclude weak refs")
    out.println("</li>")
    out.println("<li>")
    printAnchor("objectRootsIncWeak/" + hexString(id),"Include weak refs")
    out.println("</li>")
    out.println("</ul>")

    printAnchor("reachableFrom/" +hexString(id),"Objects reachable from here")
    out.println("<br/>")
  }
}