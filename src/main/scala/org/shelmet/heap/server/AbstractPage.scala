package org.shelmet.heap.server

import org.shelmet.heap.util.Misc
import java.io.PrintWriter
import org.eclipse.mat.snapshot.model._
import org.eclipse.mat.snapshot.ISnapshot
import org.eclipse.mat.SnapshotException
import scala.Some

abstract class AbstractPage(snapshot : ISnapshot) {

  protected var out: PrintWriter = null

  def run()

  private[server] def setOutput(o: PrintWriter) {
    this.out = o
  }

  def findObjectByQuery(query : String) : Option[IObject] = {
    try {
      if (query.startsWith("0x")) {
        val addr = Misc.parseHex(query)
        Some(snapshot.getObject(snapshot.mapAddressToId(addr)))
      } else {
        try {
          val addr = query.toLong
          Some(snapshot.getObject(snapshot.mapAddressToId(addr)))
        } catch {
          case  nfe : NumberFormatException =>
            import scala.collection.JavaConversions._
            snapshot.getClassesByName(query,false).headOption
        }
      }

    } catch {
      case x : SnapshotException =>
        None
    }
  }

  def table( c : => Unit ) {
    out.println("""<table class="table table-bordered table-condensed">""")
    c
    out.println("</table>")
  }

  def tableRow( c : => Unit ) {
    out.println("<tr>")
    c
    out.println("</tr>")
  }

  def ul[A](values : Iterable[A],f : A=>Unit) {
    tagPair("ul",{
      values foreach { a =>
        tagPair("li",f(a))
      }
    })
  }

  def tableData( s : String ) { tableData(out.println(s)) }

  private def tagPair(token : String,f : => Unit)  {
    out.println(s"<$token>")
    f
    out.println(s"</$token>")
  }

  def tableHeader(text : String ) { tagPair("th",out.println(text)) }

  def tableData( c : => Unit ) { tagPair("td",c) }

  protected def html(title : String )( c : => Unit ) {
    startHtml(title)
    c
    endHtml()
  }

  protected def h2(s : String) {
    h2(out.print(s))
  }

  protected def pageAnchor(anchorName : String) {
    out.println(s"""<a name="$anchorName"></a>""")
  }

  protected def h2( c : => Unit ) {
    out.print("<h2>")
    c
    out.println("</h2>")
  }

  private def startHtml(title: String) {
    val encodedTitle = Misc.encodeHtml(title)
    out.println(s"""<!DOCTYPE html>
                  |<html lang="en">
                  |  <head>
                  |    <meta charset="utf-8"/>
                  |    <title>$encodedTitle</title>
                  |    <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                  |    <meta name="description" content=""/>
                  |    <meta name="author" content=""/>
                  |
                  |    <link href="/css/bootstrap.css" rel="stylesheet"/>
                  |    <style type="text/css">
                  |      body {
                  |        padding-top: 60px;
                  |        padding-bottom: 40px;
                  |      }
                  |    </style>
                  |    <link href="/css/bootstrap-responsive.css" rel="stylesheet"/>
                  |
                  |    <!-- HTML5 shim, for IE6-8 support of HTML5 elements -->
                  |    <!--[if lt IE 9]>
                  |      <script src="/js/html5shiv.js"></script>
                  |    <![endif]-->
                  |  </head>
                  |  <body>
                  |    <div class="navbar navbar-inverse navbar-fixed-top">
                  |      <div class="navbar-inner">
                  |        <div class="container">
                  |          <button type="button" class="btn btn-navbar" data-toggle="collapse" data-target=".nav-collapse">
                  |            <span class="icon-bar"></span>
                  |            <span class="icon-bar"></span>
                  |            <span class="icon-bar"></span>
                  |          </button>
                  |          <a class="brand" href="/">SHelmet</a>
                  |          <div class="nav-collapse collapse">
                  |            <ul class="nav">
                  |              <li class="active"><a href="/">Home</a></li>
                  |              <li class="dropdown">
                  |                <a href="#" class="dropdown-toggle" data-toggle="dropdown">Reports<b class="caret"></b></a>
                  |                <ul class="dropdown-menu">
                  |                  <li class="nav-header">Classes</li>
                  |                  <li><a href="/allClassesWithPlatform">Including platform</a></li>
                  |                  <li><a href="/allClassesWithoutPlatform">Excluding platform</a></li>
                  |                  <li class="divider"></li>
                  |                  <li><a href="/rootSet">RootSet</a></li>
                  |                  <li class="divider"></li>
                  |                  <li class="nav-header">Instance Counts</li>
                  |                  <li><a href="/showInstanceCountsIncPlatform">Including platform</a></li>
                  |                  <li><a href="/showInstanceCountsExcPlatform">Excluding platform</a></li>
                  |                  <li class="divider"></li>
                  |                  <li><a href="/histogram">Heap histogram</a></li>
                  |                  <li class="divider"></li>
                  |                  <li><a href="/finalizerSummary">Finalizer summary</a></li>
                  |                </ul>
                  |              </li>
                  |              <li><a href="/about">About</a></li>
                  |            </ul>
                  |          </div><!--/.nav-collapse -->
                  |        </div>
                  |      </div>
                  |    </div>
                  |
                  |    <div class="container">
                  |""".stripMargin)

    out.println(s"<h1>$encodedTitle</h1>")
  }

  protected def endHtml() {
    out.println("""|    </div> <!-- /container -->
                  |    <script src="/js/jquery-2.0.3.min.js"></script>
                  |    <script src="/js/bootstrap.js"></script>
                  |  </body>
                  |</html>
                  |""".stripMargin)

  }

  protected def printAnchor(anchorSubPath : String,content : String) {
    out.print("<a href=\"/")
    out.print(anchorSubPath)
    out.print("\">")
    out.print(content)
    out.println("</a>")
  }

  protected def printThingAnchorTag(addr: Long,content : String) {
    printAnchor("object/" + hexString(addr),content)
  }

  protected def printThing(thing: Any) {
    if (thing == null) {
      printEncoded("<null>")
      return
    }
    thing match {
      case io : IObject =>
        printThingAnchorTag(io.getObjectAddress,io.getDisplayName + " (" + io.getUsedHeapSize + " bytes)")
      case or : ObjectReference =>
        try {
        val obj = snapshot.getObject(or.getObjectId)
        printThing(obj)
        }catch {
          case s : SnapshotException =>
          out.print("DEAD REF - " + or.getObjectAddress)
        }
      case b : Boolean => printEncoded(b.toString)
      case l : Long => printEncoded(l.toString)
      case c : Char => printEncoded(c.toString)
      case d : Double => printEncoded(d.toString)
      case i : Int => printEncoded(i.toString)
      case b : Byte => printEncoded("0x" + Integer.toString(b.asInstanceOf[Int] & 0xff, 16))
      case f : Float => printEncoded("" + f)
      case _ =>
        printEncoded(thing.toString)
    }
  }

  protected def printRoot(root: GCRootInfo,rootIndex : Int) {

    if(root.getContextAddress != 0 && snapshot.getThreadStack(snapshot.mapAddressToId(root.getContextAddress)) != null)
      printAnchor("rootStack/" + hexString(root.getObjectAddress) +":" + rootIndex,root.getTypeString)
    else
      printEncoded(root.getTypeString)
  }

  protected def printClass(clazz: IClass) {
    if (clazz != null)
      printAnchor("object/"+encodeForURL(clazz),clazz.getNewDisplayName)
    else
      out.println("null")
  }

  protected def encodeForURL(clazz: IClass): String = Misc.toHex(clazz.getObjectAddress)

  protected def printField(field: FieldDescriptor) {
    printEncoded(field.getName + " (" + field.getVerboseSignature + ")")
  }

  protected def printStackTrace(trace: IThreadStack) {
    trace.getStackFrames.foreach { (f: IStackFrame) =>
      printEncoded(f.getText)
      out.println("<br/>")
    }
  }

  protected def hexString(addr: Long) = {
      Misc.toHex(addr)
  }

  protected def parseHex(value: String): Long = Misc.parseHex(value)

  protected def printEncoded(str: String) {
    out.print(Misc.encodeHtml(str))
  }
}