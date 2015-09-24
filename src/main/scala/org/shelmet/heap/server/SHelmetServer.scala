package org.shelmet.heap.server

import akka.event.slf4j.SLF4JLogging
import org.shelmet.heap.model.Snapshot
import akka.actor.{ActorSystem, Props}
import akka.io.IO
import spray.can.Http
import akka.pattern.Patterns
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.io.Tcp.{Bound, CommandFailed}
import org.shelmet.heap.Config
import org.shelmet.heap.parser.HprofReader

object SHelmetServer extends SLF4JLogging {
  def apply(config: Config) = {
    val dumpFile = config.dumpFile
    log.info(s"Reading from $dumpFile...")
    val reader = new HprofReader(dumpFile.getAbsolutePath)
    val model = Snapshot.readFromDump(reader, config.trackObjectAllocationStacks, config.trackReferencesToObjects)
    new SHelmetServer(config.port, model)
  }
}
class SHelmetServer private (port: Int, snapshot: Snapshot) extends SLF4JLogging {

  implicit var system : ActorSystem = null

  def start() : Option[Int] = {
    system = ActorSystem("shelmet")
    val service = system.actorOf(Props(new QueryServiceActor(snapshot)), "web-service")
    log.info("Initialising web server")
    val res = Patterns.ask(IO(Http),Http.Bind(service, "localhost", port),5000)
    Await.result(res,5 seconds) match {
      case f : CommandFailed =>
        log.error(s"Binding to http port $port failed")
        system.shutdown()
        system = null
        None
      case b : Bound =>
        val boundPort = b.localAddress.getPort
        log.info(s"Binding to http port $boundPort successful")
        Some(boundPort)
      case x : AnyRef =>
        log.error(s"Got unexpected response to http bind command: $x")
        system.shutdown()
        system = null
        None
    }
  }

  def stop() {
    // wait for the http part to shutdown nicely first before
    // terminating the actor system (otherwise you get random exceptions)
    Patterns.ask(IO(Http),Http.Unbind,5000)
    system.shutdown()
  }
}