package org.shelmet.heap.server

import org.shelmet.heap.model.Snapshot
import akka.actor.{ActorSystem, Props}
import akka.io.IO
import spray.can.Http
import akka.pattern.Patterns
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.io.Tcp.{Bound, CommandFailed}
import com.typesafe.scalalogging.slf4j.Logging
import org.shelmet.heap.Config
import org.shelmet.heap.parser.HprofReader
import org.eclipse.mat.parser.internal.SnapshotFactoryImpl
import java.io.File
import org.eclipse.mat.util.ConsoleProgressListener
import org.eclipse.mat.snapshot.ISnapshot

object SHelmetServer extends Logging {
  def apply(config : Config) = {
    val dumpFile = config.dumpFile
    logger.info(s"Reading from $dumpFile...")
    val reader = new HprofReader(dumpFile.getAbsolutePath)

    val snapshot = Snapshot.readFromDump(reader,config.trackObjectAllocationStacks,config.trackReferencesToObjects)

    // new snapshot handling
    val factory = new SnapshotFactoryImpl()
    val newSnapshot = factory.openSnapshot(config.dumpFile, new java.util.HashMap[String, String](),
      new ConsoleProgressListener(System.out))

    new SHelmetServer(config.port, snapshot,newSnapshot)
  }
}

class SHelmetServer private (port: Int, oldSnapshot: Snapshot,newSnapshot : ISnapshot) extends Logging {

  implicit var system : ActorSystem = null

  def start() : Option[Int] = {
    system = ActorSystem("shelmet")
    val service = system.actorOf(Props(new QueryServiceActor(oldSnapshot,newSnapshot)), "web-service")
    logger.info("Initialising web server")
    val res = Patterns.ask(IO(Http),Http.Bind(service, "localhost", port),5000)
    Await.result(res,5 seconds) match {
      case f : CommandFailed =>
        logger.error(s"Binding to http port $port failed")
        system.shutdown()
        system = null
        None
      case b : Bound =>
        val boundPort = b.localAddress.getPort
        logger.info(s"Binding to http port $boundPort successful")
        Some(boundPort)
      case x : AnyRef =>
        logger.error(s"Got unexpected response to http bind command: $x")
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