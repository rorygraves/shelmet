package org.shelmet.heap

import org.shelmet.heap.server.SHelmetServer
import com.typesafe.scalalogging.slf4j.Logging

object Main extends Logging {
  // TODO Planned - Calculate retained sized
  // TODO Planned - Overflow dump to disk allowing examination of larger heaps
  // TODO Planned - An OQL equivalent using Scala and dynamic evaluation
  // TODO Planned - better displays//higher level views of collection classes.
  // TODO Planned - Dump comparisons (e.g. to detect leaks).
  // TODO Planned - Connect and take dump from within application
  // TODO Planned - Update/provide utilities to provide a better dump/easier to change
  // TODO Ideas - Updates to use  /class/<classname>/<#instance> - i.e. similar to JVisualVM to improve links/display


  val PROGRAM_NAME = "SHelmet"
  val VERSION = "v0.1"

  val parser = new scopt.OptionParser[Config]("SHelmet") {
    head("SHelmet",VERSION)
    note("""  -J<flag>
           |        Pass <flag> directly to the runtime system. For")
           |        example, -J-mx512m to use a maximum heap size of 512MB")""".stripMargin)
    opt[Int]('p', "port") action { (x, c) =>
      c.copy(port = x) } text(s"Set the port for the HTTP server.  Defaults to ${Config.DEFAULT_HTTP_PORT}")
    opt[Unit]("noStack") action { (_, c) =>
      c.copy(trackObjectAllocationStacks = false) } text("Turn off tracking object allocation call stack.")
    opt[Unit]("noRefs") action { (_, c) =>
      c.copy(trackReferencesToObjects = false) } text("Turn off tracking of references to objects")
    help("help") text("Displays the programming command line options")
    version("version") text("Displays the program version")
    arg[java.io.File]("<heapFile>") required() maxOccurs(1) action { (x,c) =>
      c.copy(dumpFile = x) } text("The heap dump file to read")
  }

  def main(args: Array[String]) {

    parser.parse(args, Config()) map { config =>
      try {
        val server = SHelmetServer(config)
        server.start() match {
          case Some(portNum) =>
            logger.info("Started HTTP server on port " + portNum)
            logger.info("Server is ready.")
          case None =>
            logger.error("Failed to start web server, shutting down")
            System.exit(1)
        }
      } catch {
        case e: Throwable =>
          logger.error(s"Failed to parse file ${config.dumpFile}",e)
          System.exit(1)
      }
    } getOrElse {
      // arguments are bad, usage message will have been displayed
    }
  }
}