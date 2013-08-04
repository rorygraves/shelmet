package org.shelmet.heap

import java.io.File

object Config {
  val DEFAULT_HTTP_PORT = 8080
}

case class Config(port : Int = Config.DEFAULT_HTTP_PORT,
                  trackObjectAllocationStacks : Boolean = true,
                  trackReferencesToObjects : Boolean = true,
                  dumpFile : File = new File(".") ) {

}
