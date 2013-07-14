package org.shelmet.heap.server

import org.shelmet.heap.model.Snapshot

class AboutPage(snapshot : Snapshot) extends QueryHandler(snapshot) {
  override def run() {
    html("About SHelmet") {
      out.println(
       // TODO Find the github address
       // TODO Expand the content here.
        """| SHelmet is a re-implemention of JHat see the source at: www.github.com""".stripMargin
      )
    }
  }
}
