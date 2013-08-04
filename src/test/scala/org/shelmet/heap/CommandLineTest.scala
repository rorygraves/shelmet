package org.shelmet.heap

import org.scalatest._

class CommandLineTest extends FlatSpec {
  "A command line parser" should
    "parse default options" in {
      Main.parser.parse(Seq("test.bin"),new Config()) map { config =>
        assert(config.port === Config.DEFAULT_HTTP_PORT)
        assert(config.dumpFile.getName === "test.bin")
      } getOrElse {
        // arguments are bad, usage message will have been displayed
        fail("Should not reach here")
      }
  }
}
