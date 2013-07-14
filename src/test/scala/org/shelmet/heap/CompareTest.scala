package org.shelmet.heap

import scala.Predef._
import org.shelmet.heap.parser.HprofReader
import java.io.File
import org.shelmet.heap.model.Snapshot
import com.typesafe.scalalogging.slf4j.Logging
import scala.collection.SortedMap

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
import org.scalatest.FlatSpec

class CompareTest extends FlatSpec with Logging {

  val pages: SortedMap[String, String] = SortedMap(
    "home" -> "http://localhost:8080/",
    "about" -> "http://localhost:8080/about/",
    "allClassesWithoutPlatform" -> "http://localhost:8080/allClassesWithoutPlatform/",
    "allClassesWithPlatform" -> "http://localhost:8080/allClassesWithPlatform/",
    "rootSet" -> "http://localhost:8080/showRoots/",
    "histogram" -> "http://localhost:8080/histo/",
    "finalizers" -> "http://localhost:8080/finalizerSummary/",
    "reachables" -> "http://localhost:8080/reachableFrom/0x7f4554128",
    "object" -> "http://localhost:8080/object/0x7f4554d90",
    "object2" -> "http://localhost:8080/object/0x7f44c0cb8", // object array
    "object3" -> "http://localhost:8080/object/0x7f44e95e8", // int array

    "rootStack" -> "http://localhost:8080/rootStack/0x1",

    "roots" -> "http://localhost:8080/roots/0x7f44cacb0",
    "allroots" -> "http://localhost:8080/allRoots/0x7f44cacb0",
    "class" -> "http://localhost:8080/object/0x7fae220b0",
    "class2" -> "http://localhost:8080/object/0x7faea2a30",
    "classNotFound" -> "http://localhost:8080/object/0xfffffff",
    "instances1" -> "http://localhost:8080/instances/0x7faeff1f8",
    "excludeSubclasses" -> "http://localhost:8080/instances/0x7fae220b0",
    "includeSubclasses" -> "http://localhost:8080/allInstances/0x7fae220b0",
    "instanceCountsIncPlatform" -> "http://localhost:8080/showInstanceCountsIncPlatform",
    "instanceCountsExcPlatform" -> "http://localhost:8080/showInstanceCountsExcPlatform",
    "refsByType1" -> "http://localhost:8080/refsByType/0x7fb27e868"
  )

  "SHelmet" should "compare correctly against the expectations" in {
    // TODO change CompareTest to compare on each page rather than storing all pages in memory

    def runTest(requests: SortedMap[String, String]): (SortedMap[String, String],Long) = {
      val startTime = System.currentTimeMillis()

      import org.shelmet.heap.server.QueryListener

      val model = {
        val reader = new HprofReader("heap.bin")
        Snapshot.readFromDump(reader,callStack = true,calculateRefs = true)
      }

      logger.info("Initialising server")

      // 0 binds to a free ephemeral report
      val listener = new QueryListener(0,model)

      listener.start() match {
        case Some(port) =>
          try {
            val results = executeReports(listener.system,port)
            val endTime = System.currentTimeMillis()
            (results,endTime-startTime)
          } finally {
            listener.stop()
          }
        case None =>
          logger.error("Failed to start server")
          fail("Unable to start server instance")
          (SortedMap.empty,0)
      }
    }

    def executeReports(givenSystem : ActorSystem,port : Int) = {

      implicit val system : ActorSystem = givenSystem
      Thread.sleep(200)
      val results = SortedMap[String,String]() ++ pages.map {
        case (name, url) =>
          val fixedUrl = url.replace("localhost:8080/",s"localhost:$port/")
          logger.info("Running: " + name)
          implicit val timeout : Timeout = 20.seconds
          val response: Future[HttpResponse] = (IO(Http) ? HttpRequest(GET, Uri(fixedUrl))).mapTo[HttpResponse]
          val res: HttpResponse = Await.result(response,10 second)
          val str = res.entity.asString
          val file = new File("src/test/resources/compare/actual/" + name + ".html")
          printToFile(file) { _.write(str) }
          (name, str)
      }

      results
    }

    def printToFile(f: java.io.File)(op: java.io.PrintWriter => Unit) {
      val p = new java.io.PrintWriter(f)
      try { op(p) } finally { p.close() }
    }


    def readExpected(key : String) : Option[String] = {
      val file = new File("src/test/resources/compare/expected/" + key + ".html")
      val res = if(file.exists())
        scala.io.Source.fromFile(file, "utf-8").getLines().mkString("\n") + "\n"
      else
      // we return a file with a random content which will parse as XML, but always be unique
        s"<html><body>MISSING FILE${Random.nextInt()}</body></html>"

      Some(res)
    }

    val (results,newTime) = runTest(pages)

    println("---------------------------------------------")
    val res = SortedMap[String, (String, String, Boolean)]() ++ {
      for (key <- pages.keys ;
           left <- readExpected(key) ;
           right <- results.get(key))
      yield {
        val filesEqual = try {
          XMLUnit.compareXML(left,right).identical()
        } catch {
          case e : Throwable => throw new RuntimeException("Cant compare " + key,e)
        }
        (key, (left, right, filesEqual))
      }
    }.toMap

    val (matched, failed) = res.partition {
      case (_, (_, _, m)) =>
        m
    }

    println("Matching: ")
    matched foreach {
      case (name, _) =>
        println("  " + name)
    }

    println("Mismatched: ")
    failed foreach {
      case (name, (a, b, _)) =>
        println("  " + name + " mismatched")
    }


    logger.info("test took = " + newTime)
    assert(failed.size === 0)
  }
}
