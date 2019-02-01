package monika.server.script

import monika.server.Structs.FutureAction
import monika.server.UseDateTime
import monika.server.script.internal.Unlock

import scala.util.Try

object DelayUnlock extends Script with UseDateTime {

  override def run(args: Vector[String]): SC[Unit] = {
    if (args.isEmpty) printLine("usage: delay-unlock <minutes>")
    else if (Try(args(0).toInt).filter(_ > 0).isFailure) printLine("minutes must be a positive integer")
    else delayUnlockForMinutes(minutes = args(0).toInt)
  }

  private def delayUnlockForMinutes(minutes: Int): SC[Unit] = for {
    state <- getState()
    _ <- state.queue.indexWhere(_.script == Unlock) match {
      case -1 => printLine("no unlock found")
      case index => {
        val oldAct = state.queue(index)
        val newAct = FutureAction(oldAct.at.plusMinutes(minutes), Unlock)
        val newQueue = state.queue |> removeAt(index) |> addItems(newAct)
        printLine(s"unlock moved to ${newAct.at.format(DefaultFormatter)}")
        setState(state.copy(queue = newQueue))
      }
    }
  } yield Unit

}
