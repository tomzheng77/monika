package monika.server.script.library

import monika.server.Structs.Action
import monika.server.{UseDateTime, UseScalaz}

trait QueueOps extends UseScalaz with UseDateTime {

  def removeAt(index: Int)(queue: Vector[Action]): Vector[Action] = {
    if (!queue.indices.contains(index)) queue
    else queue.take(index) ++ queue.drop(index + 1)
  }

  def addItems(items: Action*)(queue: Vector[Action]): Vector[Action] = {
    (queue ++ items).sortBy(_.at)
  }

}
