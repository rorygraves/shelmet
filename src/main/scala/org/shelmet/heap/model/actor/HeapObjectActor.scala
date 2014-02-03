package org.shelmet.heap.model.actor

import akka.actor.{ActorLogging, Actor}
import org.shelmet.heap.model.Snapshot

object HeapObjectActor {

}

class HeapObjectActor(snapshot : Snapshot) extends Actor with ActorLogging {


  def receive = {
    case x : Any =>
  }
}
