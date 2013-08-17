package org.shelmet.heap

import scala.Predef._
import java.io.File
import com.typesafe.scalalogging.slf4j.Logging

import scala.concurrent.duration._
import akka.io.IO
import akka.pattern.ask
import spray.can.Http
import spray.http._
import HttpMethods._
import scala.concurrent.{Await, Future}
import akka.actor.ActorSystem
import akka.util.Timeout
import org.custommonkey.xmlunit.XMLUnit
import scala.util.Random
import org.scalatest.{FunSuite, BeforeAndAfterAll}
import org.shelmet.heap.server.SHelmetServer

class CompareTest extends FunSuite with Logging with BeforeAndAfterAll {

  var startTime : Long = 0

  var server : SHelmetServer = null
  var port : Int = -1

  override protected def beforeAll() {

    startTime = System.currentTimeMillis()

    logger.info("Initialising server")

    // 0 binds to a free ephemeral report
    server = SHelmetServer(new Config(0,true,true,new File("heap.bin")))

    server.start() match {
      case Some(actualPort) =>
        this.port = actualPort
      case None =>
        logger.error("Failed to start server")
        fail("Unable to start server instance")
    }
  }

  override protected def afterAll() {
    server.stop()
    val endTime = System.currentTimeMillis()
    logger.info("test took = " + (endTime - startTime))
  }

  def readExpected(key : String) : String = {
    val file = new File("src/test/resources/compare/expected/" + key + ".html")
    if(file.exists())
      scala.io.Source.fromFile(file, "utf-8").getLines().mkString("\n") + "\n"
    else
    // we return a file with a random content which will parse as XML, but always be unique
      s"<html><body>MISSING FILE${Random.nextInt()}</body></html>"
  }

  def printToFile(f: java.io.File)(op: java.io.PrintWriter => Unit) {
    val p = new java.io.PrintWriter(f)
    try { op(p) } finally { p.close() }
  }

  def testPage(name : String,url : String) {
    val fixedUrl = url.replace("localhost:8080/",s"localhost:$port/")
    logger.info("Running: " + name)
    implicit val timeout : Timeout = 20.seconds
    implicit val system : ActorSystem = server.system
    val response: Future[HttpResponse] = (IO(Http) ? HttpRequest(GET, Uri(fixedUrl))).mapTo[HttpResponse]
    val res: HttpResponse = Await.result(response,20 second)
    val str = res.entity.asString
    val file = new File("src/test/resources/compare/actual/" + name + ".html")
    printToFile(file) { _.write(str) }

    val expected = readExpected(name)

    assert(XMLUnit.compareXML(expected,str).identical(),s"Report $name not equal")
  }

  test("Render homepage") {
    testPage("home","http://localhost:8080/")
  }

  test("Render About page") {
    testPage("about","http://localhost:8080/about/")
  }

  test("Render AllClassesWithoutPlatform page") {
    testPage("allClassesWithoutPlatform","http://localhost:8080/allClassesWithoutPlatform/")
  }

  test("Render AllClassesWithPlatform page") {
    testPage("allClassesWithPlatform","http://localhost:8080/allClassesWithPlatform/")
  }

  test("Render RootSet page") {
    testPage("rootSet","http://localhost:8080/rootSet/")
  }

  test("Render Finalizer Objects page") {
    testPage("finalizerObjects","http://localhost:8080/finalizerObjects/")
  }

  test("Render Histogram page") {
    testPage("histogram","http://localhost:8080/histogram/")
  }

  test("Render Histogram page (sorted by class)") {
    testPage("histogramByClass","http://localhost:8080/histogram/class")
  }

  test("Render Histogram page (by count)") {
    testPage("histogramByCount","http://localhost:8080/histogram/count")
  }

  test("Render Finalizers page") {
    testPage("finalizers","http://localhost:8080/finalizerSummary/")
  }

  test("Render Reachables page") {
    testPage("reachables","http://localhost:8080/reachableFrom/0x7f4554128")
  }

  test("Render Object page (basic object)") {
    testPage("object","http://localhost:8080/object/0x7f4554d90")
  }

  test("Render Object page (java.lang.String)") {
    testPage("objectString","http://localhost:8080/object/0x7f45682a8")
  }

  test("Render Object page (object array)") {
    testPage("objectObjectArray","http://localhost:8080/object/0x7f44c0cb8")
  }

  test("Render Object page (int array)") {
    testPage("objectIntArray","http://localhost:8080/object/0x7f44e95e8")
  }

  test("Render RootStack page") {
    testPage("rootStack","http://localhost:8080/rootStack/0x1")
  }

  test("Render RootStack page (illegal/notFound)") {
    testPage("rootStackIllegal","http://localhost:8080/rootStack/-5")
  }

  test("Render RootStack page (no stack)") {
    testPage("rootStackNoStack","http://localhost:8080/rootStack/150")
  }

  test("Render Object Roots page (Excluding Weak Refs)") {
    testPage("objectRootsExcWeak","http://localhost:8080/objectRootsExcWeak/0x7f44cacb0")
  }

  test("Render Object Roots page - not found") {
    testPage("objectRootsExcWeakNotFound","http://localhost:8080/objectRootsExcWeak/0x7f44fd229")
  }

  test("Render Object Roots page (Including Weak Refs)") {
    testPage("objectRootsIncWeak","http://localhost:8080/objectRootsIncWeak/0x7f44cacb0")
  }

  test("Render class Object page") {
    testPage("class","http://localhost:8080/object/0x7fae220b0")
  }

  test("Render alternate class object page") {
    testPage("class2","http://localhost:8080/object/0x7faea2a30")
  }

  test("Render Object page (not found)") {
    testPage("classNotFound","http://localhost:8080/object/0xfffffff")
  }

  test("Render Instances page") {
    testPage("instances1","http://localhost:8080/instances/0x7faeff1f8")
  }

  test("Render Instances page (of non-class)") {
    testPage("instancesOfNonClass","http://localhost:8080/instances/0x7f44c0018")
  }

  test("Render Instances page (not found)") {
    testPage("instancesNotFound","http://localhost:8080/instances/0x7f44c0019")
  }

  test("Render Instances page (excluding subclasses)") {
    testPage("excludeSubclasses","http://localhost:8080/instances/0x7fae220b0")
  }

  test("Render Instances page (including subclasses)") {
    testPage("includeSubclasses","http://localhost:8080/allInstances/0x7fae220b0")
  }

  test("Render InstanceCounts page (including platform)") {
    testPage("instanceCountsIncPlatform","http://localhost:8080/showInstanceCountsIncPlatform")
  }

  test("Render InstanceCounts page (excluding platform)") {
    testPage("instanceCountsExcPlatform","http://localhost:8080/showInstanceCountsExcPlatform")
  }

  test("Render RefsByType page") {
    testPage("refsByType1","http://localhost:8080/refsByType/0x7fb27e868")
  }

  test("Render RefsByType page (of non-class)") {
    testPage("refsByTypeNonObject","http://localhost:8080/refsByType/0x7f44c0018")
  }

  test("Render RefsByType page (missing object)") {
    testPage("refsByTypeMissing","http://localhost:8080/refsByType/0x7f44c0019")
  }

}
