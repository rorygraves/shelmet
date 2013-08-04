package org.shelmet.heap.server

import akka.actor.Actor
import spray.routing.HttpService
import spray.http._
import MediaTypes._
import org.shelmet.heap.model.Snapshot
import java.io.{OutputStreamWriter, PrintWriter, ByteArrayOutputStream}
import spray.routing.directives.LogEntry
import akka.event.Logging

class QueryServiceActor(val snapshot: Snapshot) extends Actor with QueryService {

  def actorRefFactory = context
  def receive = runRoute(queryRoute)
}

// this trait defines our service behavior independently from the service actor
trait QueryService extends HttpService {

  val snapshot: Snapshot

  def showPath(req: HttpRequest) = LogEntry("Method = %s, Path = %s" format(req.method, req.uri), Logging.InfoLevel)


  // we use the enclosing ActorContext's or ActorSystem's dispatcher for our Futures and Scheduler
  implicit def executionContext = actorRefFactory.dispatcher

  val queryRoute = {
    respondWithMediaType(`text/html`) {
      get {
        path("") {
          complete(runQuery(new HomepagePage(snapshot)))
        } ~
        path("about") {
          complete(runQuery(new AboutPage(snapshot)))
        } ~
        path("allClassesWithoutPlatform") {
          complete(runQuery(new AllClassesPage(snapshot,true)))
        } ~
        path("allClassesWithPlatform") {
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
        path("reachableFrom" / Segment) { param =>
          complete(runQuery(new ReachablePage(snapshot,param)))
        } ~
        path("rootStack" / Segment) { param =>
          complete(runQuery(new RootStackPage(snapshot,param)))
        } ~
          path("histo" / Segment) { param =>
            complete(runQuery(new HistogramPage(snapshot,param)))
        } ~
        path("histo") {
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
    } ~
      path(Rest) { path =>
        getFromResource("bootstrap/%s" format path)
      }
  }

  def runQuery(query : AbstractPage) : String = {
    // TODO make this emit something nice on failure
    // TODO update the writer so it chunks the results rather than collecting it to a string and output as one - stops OOM on large results
    val baos = new ByteArrayOutputStream()
    val pw = new PrintWriter(new OutputStreamWriter(baos))
    query.setOutput(pw)
    query.run()
    pw.flush()
    baos.toString
  }
}