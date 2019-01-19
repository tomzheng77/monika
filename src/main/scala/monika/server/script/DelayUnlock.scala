package monika.server.script

import monika.server.Structs.FutureAction
import monika.server.UseDateTime
import monika.server.script.internal.Unlock

import scala.util.Try

object DelayUnlock extends Script with UseDateTime {

  override def run(args: Vector[String]): SC[Unit] = {
    if (args.isEmpty) printLine("usage: delay-unlock <minutes>")
    else if (Try(args(0).toInt).filter(_ > 0).isFailure) printLine("minutes must be a positive integer")
    else {
      val minutes = args(0).toInt
      SC(api => {
        api.transaction(state => {
          state.queue.lastOption match {
            case None => api.printLine("the queue is empty"); (state, Unit)
            case Some(future) if future.script != Unlock => api.printLine("queue does not end with an unlock"); (state, Unit)
            case Some(FutureAction(at, Unlock, scriptArgs)) => {
              val newAt = at.plusMinutes(minutes)
              api.enqueue(newAt, Unlock, scriptArgs)
              api.printLine(s"unlock moved to ${newAt.format(DefaultFormatter)}")
              (state.copy(queue = state.queue.dropRight(1)), Unit)
            }
          }
        })
      })
    }
  }

}
