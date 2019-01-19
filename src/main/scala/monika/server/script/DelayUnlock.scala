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
          state.queue.indexWhere(act => act.script == Unlock) match {
            case -1 => api.printLine("no unlock found"); (state, Unit)
            case index => {
              val oldAct = state.queue(index)
              val newAct = FutureAction(oldAct.at.plusMinutes(minutes), Unlock)
              api.printLine(s"unlock moved to ${newAct.at.format(DefaultFormatter)}")
              (state.copy(queue = state.queue |> removeAt(index) |> addItems(newAct)), Unit)
            }
          }
        })
      })
    }
  }

}
