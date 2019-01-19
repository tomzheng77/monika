package monika.server.script.library

import monika.server.Structs.FutureAction
import monika.server.{UseDateTime, UseScalaz}

trait QueueOps extends UseScalaz with UseDateTime {

  def removeAt(index: Int)(queue: Vector[FutureAction]): Vector[FutureAction] = {
    if (!queue.indices.contains(index)) queue
    else queue.take(index) ++ queue.drop(index + 1)
  }

  def addItems(items: FutureAction*)(queue: Vector[FutureAction]): Vector[FutureAction] = {
    (queue ++ items).sortBy(_.at)
  }

}
