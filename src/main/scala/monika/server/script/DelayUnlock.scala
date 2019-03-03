package monika.server.script

import monika.server.Structs.{Action, MonikaState}
import monika.server.UseDateTime
import monika.server.script.internal.Unlock

import scala.util.Try

object DelayUnlock extends Script with UseDateTime {

  override def run(args: Vector[String]): IOS[Unit] = {
    if (args.isEmpty) printLine("usage: delay-unlock <minutes>")
    else if (Try(args(0).toInt).filter(_ > 0).isFailure) printLine("minutes must be a positive integer")
    else getState().flatMap(st => delayUnlockForMinutes(state = st, minutes = args(0).toInt))
  }

  private def delayUnlockForMinutes(state: MonikaState, minutes: Int): IOS[Unit] = {
    state |> indexOfUnlock match {
      case -1 => printLine("no unlock found")
      case index => delayUnlockAtIndexForMinutes(state, index, minutes)
    }
  }

  private def indexOfUnlock(state: MonikaState): Int = {
    state.queue.indexWhere(_.script == Unlock)
  }

  private def delayUnlockAtIndexForMinutes(state: MonikaState, index: Int, minutes: Int): IOS[Unit] = {
    val oldAction = state.queue(index)
    val newAction = Action(oldAction.at.plusMinutes(minutes), Unlock)
    val newQueue = state.queue |> removeAt(index) |> addItems(newAction)
    steps(
      printLine(s"unlock moved to ${newAction.at.format()}"),
      setState(state.copy(queue = newQueue))
    )
  }

}
