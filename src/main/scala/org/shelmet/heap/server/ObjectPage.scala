package org.shelmet.heap.server

import org.eclipse.mat.snapshot.ISnapshot
import org.eclipse.mat.snapshot.model._
import org.eclipse.mat.SnapshotException
import org.shelmet.heap.util.Misc

class ObjectPage(snapshot : ISnapshot,query : String) extends AbstractPage(snapshot) {

  override def run() {
    findObjectByQuery(query) match {
      case None =>
        html("Missing object " + query) {
          out.println("object not found")
        }
      case Some(clazz : IClass) =>
        renderClass(clazz)
      case Some(jObject : IInstance) =>
        renderInstance(jObject)
      case Some(array : IPrimitiveArray) =>
        renderValueArray(array)
      case Some(objArray : IObjectArray) =>
        renderObjectArray(objArray)
      case x =>
        throw new IllegalStateException("Unknown heap type " + x)
    }
  }

  def renderClass(clazz : IClass) {
    val name = s"class ${clazz.getDisplayName} (${clazz.getObjectAddress.toHexString})"
    html(name) {

      import scala.collection.JavaConversions._
      val subClasses = clazz.getAllSubclasses.toList
      val subclassCount = subClasses.size

      val instanceFields = clazz.getFieldDescriptors.sortWith((jf1,jf2) => jf1.getName.compareTo(jf2.getName) < 0)
      val instanceFieldsCount = instanceFields.size

      val staticFields = clazz.getStaticFields
      val staticFieldsCount = staticFields.size

      basicObjectRender(clazz) {
        tableRow {
          tableData { out.println("<b>Superclass:</b>") }
          tableData { printClass(clazz.getSuperClass) }
        }
        tableRow {
          tableData { out.println("<b>ClassLoader:</b>") }
          tableData { printThing(clazz.getClassLoader) }
        }
        // TODO Not support by MAT
//        tableRow {
//          tableData { out.println("<b>Signers:</b>") }
//          tableData { printThing(oldClazz.getSigners.getOrElse(null)) }
//        }
//        tableRow {
//          tableData { out.println("<b>Protection Domain:</b>") }
//          tableData { printThing(oldClazz.getProtectionDomain.getOrElse(null)) }
//        }
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
          ul[IClass](subClasses,printClass)
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
                  tableData(static.getName)
                  tableData(static.getVerboseSignature)
                  tableData(printThing(static.getValue))
                }
            }
          }
        }

        val instanceCountWithSub = clazz.getNumberOfObjects(true)
        h2(s"Instances ($instanceCountWithSub)")
        if(instanceCountWithSub != 0) {
          if(subClasses.isEmpty)
            printAnchor(s"instances/${encodeForURL(clazz)}",s"of this class (${clazz.getNumberOfObjects})")
          else {
            printAnchor(s"instances/${encodeForURL(clazz)}",s"of this class (${clazz.getNumberOfObjects})")
            out.println("<br/>")
            printAnchor("allInstances/" + encodeForURL(clazz),s"of this class with subclasses ($instanceCountWithSub)")
          }
        }

        h2("References summary by Type")
        printAnchor("refsByType/" + encodeForURL(clazz),"References summary by type")
      }
    }
  }

  def renderInstance(obj : IInstance) {
    html(s"instance of ${obj.getClazz.getName} #${obj.getObjectId}") {

      basicObjectRender(obj) {
        // no extra table rows
      } {
      // special string handling
        obj match {
          case si : IInstance if si.getClazz.getName == "java.lang.String" =>
            val bytesObjectRef = si.getField("value").getValue.asInstanceOf[ObjectReference]
            val strValue = new String(bytesObjectRef.getObject.asInstanceOf[IPrimitiveArray].valueString(true))
            h2("Value:")
            out.println(strValue)
          case _ =>
        }

        h2("Instance Fields:")
        import scala.collection.JavaConversions._
        val fields = obj.getFields
        table {
          tableRow {
            tableHeader("Field")
            tableHeader("Type")
            tableHeader("Value")
          }
          fields foreach { field =>
              tableRow {
                tableData(field.getName)
                tableData(field.getVerboseSignature)
                tableData(printThing(field.getValue))
              }
          }
        }
      }
    }
  }

  def renderValueArray(array : IPrimitiveArray) {
    html(s"value array: ${array.toString}") {

      basicObjectRender(array) {
        // no rows
      } {
        printEncoded(array.valueString(true))
      }
    }
  }

  def renderObjectArray(objArray : IObjectArray) {

    html(s"object array: ${objArray.toString}") {
      out.println(s"<h1>Array of ${objArray.getLength} objects</h1>")

      basicObjectRender(objArray) {
//        tableRow {
//          tableData { out.println("<b>Element type:</b>") }
//          tableData { printThing(objArray.clazz.getProtectionDomain.getOrElse(null)) }
//        }
        // TODO Add ELEMENT Class
      } {
        h2("Values")
        val entries = objArray.getReferenceArray.zipWithIndex
        entries foreach { case (itemRef,idx) =>
          out.print("" + idx + " : ")
          if(itemRef == 0)
            print("<null>")
          else {
            try {
              val id = snapshot.mapAddressToId(itemRef)
              printThing(snapshot.getObject(id))
            } catch {
              case se : SnapshotException =>
                out.println("BROKEN REF - " + Misc.toHex(itemRef))
            }
          }
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
  def basicObjectRender(obj : IObject)( tableContent : => Unit )( bodyContent : => Unit )  {


    val rootRefs = snapshot.getGCRootInfo(obj.getObjectId).toList
    val rootRefCounts = rootRefs.size

    val referIds = snapshot.getInboundRefererIds(obj.getObjectId)
    val refersCount = referIds.size

    table {
      tableRow {
        tableData(out.println("<b>Instance of:</b>"))
        tableData(printClass(obj.getClazz))
      }

      tableRow {
        tableData(out.println("<b>Size</b>"))
        tableData(out.println(s"${obj.getUsedHeapSize} bytes"))
      }

      tableRow {
        tableData(out.println("<b>Retained Size</b>"))
        tableData(out.println(s"${obj.getRetainedHeapSize} bytes"))
      }

      tableContent

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
    outputRootRefs(rootRefs)

    h2("References to this object:")
    pageAnchor("refers")
    referIds.foreach {
      refId =>
        try {
          val referer = snapshot.getObject(refId)
          printThing(referer)
          import scala.collection.JavaConversions._
          printEncoded(" : " + referer.describeReferenceTo(obj).mkString("/"))
        } catch {
          case se : SnapshotException =>
            out.println("BROKEN REF - " + Misc.toHex(refId))
        }
        out.println("<br/>")
    }

    printReferencesTo(obj)
  }

  protected def printReferencesTo(obj: IObject) {
    h2("Other Queries")
    out.println("Reference Chains from Rootset")
    val addressStr = hexString(obj.getObjectAddress)
    out.println("<ul>")
    out.println("<li>")
    printAnchor(s"objectRootsExcWeak/$addressStr","Exclude weak refs")
    out.println("</li>")
    out.println("<li>")
    printAnchor(s"objectRootsIncWeak/$addressStr","Include weak refs")
    out.println("</li>")
    out.println("</ul>")
    out.println("<br/>")
  }

  def outputRootRefs(rootRefs : List[GCRootInfo]) {
    val rootRefCounts = rootRefs.size

    if(rootRefCounts > 0) {
      pageAnchor("rootRefs")
      h2("Root References:")
      ul(rootRefs,{
        r : GCRootInfo => out.println(GCRootInfo.getTypeAsString(r.getType))
      })
    }
  }
}