package org.shelmet.heap.server

import org.shelmet.heap.model.Snapshot
import java.text.SimpleDateFormat
import java.util.{TimeZone,Date}

class HomepagePage(snapshot: Snapshot) extends AbstractPage(snapshot) {

  def formatDateAsUTC(date : Date) =
  {
    val sdf = new SimpleDateFormat("dd MMM yyyy HH:mm:ss z")
    sdf.setTimeZone(TimeZone.getTimeZone("UTC"))
    sdf.format(date)
  }

  override def run() {
    html("Welcome to SHelmet") {
      out.println(
        """| Welcome to SHelmet, use the links under 'Reports' above to begin browsing your heap.
          |""".stripMargin
      )

      table {
        tableRow {
          tableData("Creation time:")
          tableData(snapshot.creationDate.map(formatDateAsUTC).getOrElse("Unknown"))
        }
        tableRow {
          tableData("No Objects:")
          tableData {
            printAnchor("showInstanceCountsIncPlatform/","" + snapshot.noObjects)
          }
        }
        tableRow {
          tableData("Classes (including system classes):")
          tableData {
            printAnchor("allClassesWithPlatform/","" + snapshot.noClasses)
          }
        }
        tableRow {
          tableData("User Classes:")
          tableData {
            printAnchor("allClassesWithoutPlatform/","" + snapshot.noUserClasses)
          }
        }
      }


      val top10 = snapshot.allObjects.toList.sortBy(-_.retainedSize).take(10)
      h2("Largest 10 objects by retained size")
      table {
        tableRow {
          tableHeader("Object")
          tableHeader("Retained Size")
        }

        top10 foreach { obj =>
          tableRow {
            tableData(printThing(obj))
            tableData(s"${obj.retainedSize}")
          }
        }
      }
    }
  }
}
