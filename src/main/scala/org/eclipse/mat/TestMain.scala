package org.eclipse.mat

import org.eclipse.mat.parser.internal.SnapshotFactoryImpl
import java.io.File
import org.eclipse.mat.util.{ConsoleProgressListener, IProgressListener}
import org.eclipse.mat.util.IProgressListener.Severity
import org.eclipse.mat.snapshot.model.{IObject, IClass}
import org.eclipse.mat.parser.model.InstanceImpl
import org.eclipse.mat.snapshot.UnreachableObjectsHistogram

object TestMain extends App {
  val factory = new SnapshotFactoryImpl()
  val snapshot = factory.openSnapshot(new File("/workspace/shelmet/heap.bin"), new java.util.HashMap[String, String](),
//  val snapshot = factory.openSnapshot(new File("/workspace/shelmet/largedump.hprof"), new java.util.HashMap[String, String](),
    new ConsoleProgressListener(System.out)

//    new IProgressListener {
//      def sendUserMessage(severity: Severity, message: String, exception: Throwable): Unit = {
//        println(s"UserMessage $severity $message,$exception")
//      }
//
//      var cancelled = false
//
//      def isCanceled: Boolean = cancelled
//
//      def done() {}
//
//      def worked(work: Int): Unit = {
////        println(s"worked $work")
//      }
//
//      def setCanceled(value: Boolean): Unit = {
//        cancelled = value
//      }
//
//      def subTask(name: String): Unit = {
////        println(s"subtask $name")
//      }
//
//      def beginTask(name: String, totalWork: Int): Unit = {
////        println(s"beginTask $name $totalWork")
//      }
//    }
//
  )

  import scala.collection.JavaConversions._

  System.out.println("classes= " + snapshot.getClasses.size() )

//  val classHisto = snapshot.getClasses.map { (x: IClass) => (x.getNumberOfObjects,x) }.toList.sortBy(_._1)
//  classHisto foreach { case (count,clazz) =>
//    println(s"$count -> ${clazz.getName}")
//    if(clazz.getNumberOfObjects > 0) {
//
//      snapshot.getObject(clazz.getObjectIds.head) match {
//        case obj : InstanceImpl =>
//          println("  " + obj.getRetainedHeapSize)
////          println(obj.getFields().headOption.map(_.getVerboseSignature))
////          println(clazz.getFieldDescriptors)
//        case _ =>
//
//      }
//    }
//
//  }
  val aa = snapshot.getSnapshotAddons(classOf[UnreachableObjectsHistogram])
  aa.getRecords.foreach { r =>
    println("D " + r.getClassName + "  " + r.getShallowHeapSize)
  }
}
