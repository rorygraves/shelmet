package org.shelmet.heap.server

import org.shelmet.heap.model._
import org.shelmet.heap.util.Misc
import java.io.PrintWriter
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import org.shelmet.heap.HeapId

abstract class QueryHandler(snapshot : Snapshot) {

  protected var out: PrintWriter = null

  def run()

  private[server] def setOutput(o: PrintWriter) {
    this.out = o
  }

  def findObjectByQuery(query : String) : Option[JavaHeapObject] = {
    if (query.startsWith("0x")) {
      val id = HeapId(Misc.parseHex(query))
      snapshot.findThing(id,createIfMissing = false)
    } else
      snapshot.findClassByName(query)
  }

  // TODO this is ugly
  protected def encodeForURL(s: String): String = {
    try {
      URLEncoder.encode(s, "UTF-8")
    } catch {
      case ex: UnsupportedEncodingException =>
        ex.printStackTrace()
        s
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

  def tableData( s : String ) { tableData(out.println(s)) }

  def tableData( c : => Unit ) {
    out.println("<td>")
    c
    out.println("</td>")
  }

  protected def html(title : String )( c : => Unit ) {
    startHtml(title)
    c
    endHtml()
  }

  protected def h2(s : String) {
    h2(out.println(s))
  }

  protected def pageAnchor(anchorName : String) {
    out.println(s"""<a name="$anchorName"></a>""")
  }

  protected def h2( c : => Unit ) {
    out.println("<h2>")
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
                  |                  <li><a href="/allClassesWithPlatform/">Including platform</a></li>
                  |                  <li><a href="/allClassesWithoutPlatform/">Excluding platform</a></li>
                  |                  <li class="divider"></li>
                  |                  <li><a href="/showRoots">RootSet</a></li>
                  |                  <li class="divider"></li>
                  |                  <li class="nav-header">Instance Counts</li>
                  |                  <li><a href="/showInstanceCountsIncPlatform/">Including platform</a></li>
                  |                  <li><a href="/showInstanceCountsExcPlatform/">Excluding platform</a></li>
                  |                  <li class="divider"></li>
                  |                  <li><a href="/histo/">Heap histogram</a></li>
                  |                  <li class="divider"></li>
                  |                  <li><a href="/finalizerSummary/">Finalizer summary</a></li>
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

  protected def printThingAnchorTag(id: HeapId,content : String) {
    printAnchor("object/" + hexString(id.id),content)
  }

  protected def printThing(thing: Any) {
    if (thing == null) {
      printEncoded("<null>")
      return
    }
    thing match {
      case ho: JavaHeapObject =>
        val id = ho.heapId.id
        if (id == -1L) {
          printEncoded(thing.toString)
        } else {
          printThingAnchorTag(ho.heapId,thing.toString + " (" + ho.size + " bytes)")
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

  protected def printRoot(root: Root) {
    val st = root.stackTrace

    val traceAvailable = st match {
      case Some(t) if !t.frames.isEmpty => true
      case _ => false
    }

    if (traceAvailable)
      printAnchor("rootStack/" + hexString(root.index),root.getDescription)
    else
      printEncoded(root.getDescription)
  }

  protected def printClass(clazz: JavaClass) {
    if (clazz != null)
      printAnchor("object/"+encodeForURL(clazz),clazz.toString)
    else
      out.println("null")
  }

  protected def encodeForURL(clazz: JavaClass): String = {
    if (clazz.heapId.id == -1)
      encodeForURL(clazz.name)
    else
      clazz.getIdString
  }

  protected def printField(field: JavaField) {
    printEncoded(field.longName + " (" + field.signature + ")")
  }

  protected def printStatic(member: JavaStatic) {
    val f: JavaField = member.field
    printField(f)
    out.print(" : ")
    if (f.isObjectField)
      printThing(member.getValue)
    else
      printEncoded(member.getValue.toString)
  }

  protected def printStackTrace(trace: StackTrace) {
    trace.frames.foreach { f =>
      out.print("<font color=\"purple\">")
      printEncoded(f.className)
      out.print("</font>")
      printEncoded("." + f.methodName + "(" + f.methodSignature + ")")
      out.print(" <bold>:</bold> ")
      printEncoded(f.sourceFileName + " line " + f.getLineNumber)
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