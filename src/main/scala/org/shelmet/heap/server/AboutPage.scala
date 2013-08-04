package org.shelmet.heap.server

import org.shelmet.heap.model.Snapshot

class AboutPage(snapshot : Snapshot) extends AbstractPage(snapshot) {
  override def run() {
    html("About SHelmet") {
      out.println(
       // TODO Expand the content here.
        """| SHelmet is a re-implementation of JHat for full information on our github page at:
           |<a href="https://github.com/rorygraves/shelmet">https://github.com/rorygraves/shelmet</a>""".stripMargin
      )
    }
  }
}
