package org.shelmet.heap.server

import akka.actor.{ActorLogging, Actor}
import spray.routing.HttpService
import spray.http._
import MediaTypes._
import java.io.{OutputStreamWriter, PrintWriter, ByteArrayOutputStream}
import akka.event.LoggingAdapter
import org.eclipse.mat.snapshot.ISnapshot

class QueryServiceActor(val snapshot : ISnapshot) extends Actor with QueryService with ActorLogging {

  def actorRefFactory = context
  def receive = runRoute(queryRoute)
}

// this trait defines our service behavior independently from the service actor
trait QueryService extends HttpService {

  val log : LoggingAdapter
  val snapshot: ISnapshot

  val queryRoute = {
    respondWithMediaType(`text/html`) {
      // run each request in a separate thread
      detach() {
        get {
          path("") {
            complete(runQuery(new HomepagePage(snapshot)))
          } ~
          path("about") {
            complete(runQuery(new AboutPage(snapshot)))
          } ~
          path("allClassesWithoutPlatform" ) {
            complete(runQuery(new AllClassesPage(snapshot,true)))
          } ~
          path("allClassesWithPlatform" ) {
            complete(runQuery(new AllClassesPage(snapshot,false)))
          } ~
          path("rootSet") {
            complete(runQuery(new RootSetPage(snapshot)))
          } ~
          path("showInstanceCountsIncPlatform") {
              complete(runQuery(new InstancesCountPage(snapshot,false)))
          } ~
          path("showInstanceCountsExcPlatform") {
              complete(runQuery(new InstancesCountPage(snapshot,true)))
          } ~
          path("instances" / Segment) { param =>
            complete(runQuery(new InstancesPage(snapshot,param,false)))
          } ~
          path("allInstances" / Segment) { param =>
            complete(runQuery(new InstancesPage(snapshot,param,true)))
          } ~
          path("object" / Segment) { param =>
            complete(runQuery(new ObjectPage(snapshot,param)))
          } ~
          path("objectRootsExcWeak" / Segment) { objectRefParam =>
            complete(runQuery(new ObjectRootsPage(snapshot,objectRefParam,false)))
          } ~
          path("objectRootsIncWeak" / Segment) { objectRefParam =>
            complete(runQuery(new ObjectRootsPage(snapshot,objectRefParam,true)))
          } ~
          path("rootStack" / Segment) { param =>
            complete(runQuery(new RootStackPage(snapshot,param)))
          } ~
          path("histogram" / Segment) { param =>
              complete(runQuery(new HistogramPage(snapshot,param)))
          } ~
          path("histogram") {
            complete(runQuery(new HistogramPage(snapshot,"")))
          } ~
          path("refsByType" / Segment) { param =>
            complete(runQuery(new RefsByTypePage(snapshot,param)))
          } ~
          path("finalizerSummary") {
            complete(runQuery(new FinalizerSummaryPage(snapshot)))
          } ~
          path("finalizerObjects") {
            complete(runQuery(new FinalizerObjectsPage(snapshot)))
          } ~
          path("favicon.ico") {
            complete(StatusCodes.NotFound)
          }
        }
      }
    } ~ path(Rest) { path =>
      getFromResource("bootstrap/%s" format path)
    }
  }

  def runQuery(query : AbstractPage) : String = {

    new HttpResponse()
    val startTime = System.currentTimeMillis()
    // TODO make this emit something nice on failure
    // TODO update the writer so it chunks the results rather than collecting it to a string and output as one - stops OOM on large results
    val baos = new ByteArrayOutputStream(16*1024)
    val pw = new PrintWriter(new OutputStreamWriter(baos))
    query.setOutput(pw)
    val qs = System.currentTimeMillis()
//    Snapshot.setInstance(oldSnapshot)
    query.run()
//    Snapshot.clearInstance()
    val qe = System.currentTimeMillis()
    println(" query run took: " + (qe -qs))
    pw.flush()
    val res = baos.toString
    val endTime = System.currentTimeMillis()
    log.info(s"Request took ${endTime-startTime} ms")
    res
  }
}