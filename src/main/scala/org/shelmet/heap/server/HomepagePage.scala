package org.shelmet.heap.server

import org.shelmet.heap.model.Snapshot
import java.text.SimpleDateFormat
import java.util.{TimeZone,Date}
import org.eclipse.mat.snapshot.ISnapshot

class HomepagePage(snapshot: Snapshot,newSnapshot : ISnapshot) extends AbstractPage(newSnapshot) {

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
          tableData(Option(newSnapshot.getSnapshotInfo.getCreationDate).map(formatDateAsUTC).getOrElse("Unknown"))
        }
        tableRow {
          tableData("No Objects:")
          tableData {
            printAnchor("showInstanceCountsIncPlatform/","" + newSnapshot.getSnapshotInfo.getNumberOfObjects)
          }
        }
        tableRow {
          tableData("Classes (including system classes):")
          tableData {
            printAnchor("allClassesWithPlatform/","" + newSnapshot.getSnapshotInfo.getNumberOfClasses)
          }
        }
        tableRow {
          tableData("User Classes:")
          tableData {
            printAnchor("allClassesWithoutPlatform/","" + snapshot.getClasses.count(!_.isPlatformClass))
          }
        }
      }


      val topDominators = newSnapshot.getImmediateDominatedIds(-1)
      val x = topDominators.map { dId => (dId,newSnapshot.getRetainedHeapSize(dId))}.sortBy( x => - x._2)

      val top10 = x.take(10)
      h2("Largest 10 objects by retained size (New)")
      table {
        tableRow {
          tableHeader("Object")
          tableHeader("Retained Size")
        }

        top10 foreach { case (objId,size) =>
          tableRow {
            tableData(printThing(newSnapshot.getObject(objId)))
            tableData(s"$size")
          }
        }
      }
    }
  }
}
