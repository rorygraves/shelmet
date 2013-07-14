package org.shelmet.heap.server

import org.shelmet.heap.model.Snapshot

class HomepagePage(snapshot: Snapshot) extends QueryHandler(snapshot) {
  override def run() {
    html("Welcome to SHelmet") {
      out.println(
        """| Welcome to SHelmet, use the links under 'Reports' above to begin browsing your heap.
          |""".stripMargin
      )
      table {
        tableRow {
          tableData("Creation time:")
          tableData(snapshot.creationDate.map(_.toString).getOrElse("Unknown"))
        }
        tableRow {
          tableData("No Objects:")
          tableData("" + snapshot.noObjects)
        }
        tableRow {
          tableData("Classes (including system classes):")
          tableData("" + snapshot.noClasses)
        }
        tableRow {
          tableData("User Classes:")
          tableData("" + snapshot.noUserClasses)
        }
      }
    }
  }
}
