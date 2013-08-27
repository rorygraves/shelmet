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
      case Some(jObject : JavaObject) =>
        renderInstance(jObject)
      case Some(clazz : JavaClass) =>
        renderClass(clazz)
      case Some(array : JavaValueArray) =>
        refactorValueArray(array)
      case Some(objArray : JavaObjectArray) =>
        renderObjectArray(objArray)
      case Some(unknown : UnknownHeapObject) =>
        renderUnknownHeapObject(unknown)
      case x =>
        throw new IllegalStateException("Unknown heap type " + x)
    }
  }

  def renderUnknownHeapObject(unknown : UnknownHeapObject) {
    html(s"Unknown Heap Object (${unknown.heapId.toHex})") {
      basicObjectRender(unknown) {
        // no extra rows
      } {
        // no body content
      }
    }
  }

  def renderClass(clazz : JavaClass) {
    val name = s"class ${clazz.displayName} (${clazz.heapId.toHex})"
    html(name) {

      val subClasses = clazz.getSubclasses
      val subclassCount = subClasses.size

      val instanceFields = clazz.fields.sortWith((jf1,jf2) => jf1.name.compareTo(jf2.name) < 0)
      val instanceFieldsCount = instanceFields.size

      val staticFields = clazz.getStatics
      val staticFieldsCount = staticFields.size

      basicObjectRender(clazz) {
        tableRow {
          tableData { out.println("<b>Superclass:</b>") }
          tableData { printClass(clazz.getSuperclass.getOrElse(null)) }
        }
        tableRow {
          tableData { out.println("<b>ClassLoader:</b>") }
          tableData { printThing(clazz.loader.getOrElse(null)) }
        }
        tableRow {
          tableData { out.println("<b>Signers:</b>") }
          tableData { printThing(clazz. getSigners.getOrElse(null)) }
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

      } {

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

          table {
            tableRow {
              tableHeader("Field")
              tableHeader("Type")
              tableHeader("Value")
            }
            staticFields foreach {
              case static =>
                tableRow {
                  tableData(static.field.longName)
                  tableData(static.field.fieldType.typeName)
                  tableData(printThing(static.getValue))
                }
            }
          }
//
//          ul(,printStatic)
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

        h2("References summary by Type")
        printAnchor("refsByType/" + encodeForURL(clazz),"References summary by type")
      }
    }
  }

  def renderInstance(obj : JavaObject) {
    html(s"instance of ${obj.getClazz.name} (${obj.heapId.toHex})") {

      basicObjectRender(obj) {
        // no extra table rows
      } {
      // special string handling
        obj match {
          case jso : JavaObject if jso.getClazz.isString =>

            val value: Any = jso.getField("value")
            val valStr = " " + Misc.encodeHtml(value match {
              case array: JavaValueArray => array.valueString()
              case _ => "null"
            })

            h2("Value:")
            out.println(valStr)
          case _ =>
        }

        h2("Instance Fields:")
        val fieldsAndValues = obj.getFieldsAndValues

        val set = fieldsAndValues.sortWith {
          case ((lhsField,lhsThing),(rhsField,rhsThing)) =>
            lhsField.longName.compareTo(rhsField.longName) < 0
        }
        table {
          tableRow {
            tableHeader("Field")
            tableHeader("Type")
            tableHeader("Value")
          }
          set foreach {
            case (field,value) =>
              tableRow {
                tableData(field.longName)
                tableData(field.fieldType.typeName)
                tableData(printThing(value))
              }
          }
        }
      }
    }
  }

  def refactorValueArray(array : JavaValueArray) {
    html(s"value array ${array.getIdString} (${array.heapId.toHex})") {

      basicObjectRender(array) {
        // no rows
      } {
        printEncoded(array.valueString(bigLimit = true))
      }
    }
  }

  def renderObjectArray(objArray : JavaObjectArray) {
    html(s"instance of ${objArray.displayName} (${objArray.heapId.toHex})") {
      val elements = objArray.elements.zipWithIndex
      out.println("<h1>Array of " + elements.size + " objects</h1>")

      basicObjectRender(objArray) {
//        tableRow {
//          tableData { out.println("<b>Element type:</b>") }
//          tableData { printThing(objArray.clazz.getProtectionDomain.getOrElse(null)) }
//        }
        // TODO Add ELEMENT Class
      } {
        h2("Values")
        elements foreach { case (item,idx) =>
          out.print("" + idx + " : ")
          printThing(item)
          out.println("<br/>")
        }
      }
    }
  }

  /**
   * Basic object rendering with injection points within the main table and body
   * @param obj The object to render
   * @param tableContent Rendering any extra table rows
   * @param bodyContent Any body content to render
   */
  def basicObjectRender(obj : JavaHeapObject)( tableContent : => Unit )( bodyContent : => Unit )  {

    val rootRefs: Set[Root] = obj.getRootReferences
    val rootRefCounts = rootRefs.size

    val refers = obj.referers.toList.sortWith((a, b) => a.toString.compareTo(b.toString) <0)
    val refersCount = refers.size


    table {
      tableRow {
        tableData(out.println("<b>Instance of:</b>"))
        tableData(printClass(obj.getClazz))
      }
      tableRow {
        tableData(out.println("<b>Size</b>"))
        tableData(out.println(s"${obj.size} bytes"))
      }

      tableContent

      tableRow {
        tableData(out.println("<b>Min/max distance to root</b>"))
        tableData(out.println(s"""${obj.minDepthToRoot}/${obj.maxDepthToRoot}"""))
      }

      tableRow {
        tableData { out.println("<b>References to this object</b>") }
        tableData {
          if(refersCount == 0)
            out.println("None")
          else
            out.println(s"""<a href="#refers">$refersCount</a>""")}
      }

      tableRow {
        tableData { out.println("<b>Root References</b>") }
        tableData {
          if(rootRefCounts == 0)
            out.println("None")
          else
            out.println(s"""<a href="#rootRefs">$rootRefCounts</a>""")}
      }
    }

    // remaining user body content
    bodyContent

    // general footer
    outputRootRefs(obj)

    h2("References to this object:")
    pageAnchor("refers")
    refers.foreach {
      ref =>
        printThing(ref)
        printEncoded(" : " + ref.describeReferenceTo(obj).mkString("/"))

        out.println(s" (${ref.minDepthToRoot}/${ref.maxDepthToRoot})")
        out.println("<br/>")
    }

    printAllocationSite(obj)
    printReferencesTo(obj)
  }

  private def printAllocationSite(obj: JavaHeapObject) {
    obj.getAllocatedFrom match {
      case Some(trace) if !trace.frames.isEmpty =>
        h2("Object allocated from:")
        printStackTrace(trace)
      case _ =>
    }
  }

  protected def printReferencesTo(obj: JavaHeapObject) {
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

  def outputRootRefs(obj : JavaHeapObject) {
    val rootRefs: Set[Root] = obj.getRootReferences
    val rootRefCounts = rootRefs.size

    if(rootRefCounts > 0) {
      pageAnchor("rootRefs")
      h2("Root References:")
      ul(rootRefs,{
        r : Root => out.println(r.toString)
      })
    }
  }
}