package monika.server.script

import java.time.LocalDateTime

import monika.server.Structs.{FutureAction, MonikaState}
import monika.server.UseDateTime
import monika.server.script.internal.Unlock
import monika.server.script.property.CanRequest

import scala.util.Try

object Request extends Script with UseDateTime {

  override def run(args: Vector[String]): SC[Unit] = {
    if (args.size < 2) printLine("usage: request <minutes> <script> <args..>")
    else if (Try(args(0).toInt).filter(_ > 0).isFailure) printLine("minutes must be a positive integer")
    else if (args(1).trim.isEmpty) printLine("script cannot be empty")
    else {
      val minutes = args(0).toInt
      val scriptName = args(1).trim
      Script.allScriptsByName.get(scriptName) match {
        case None => printLine(s"script '$scriptName' does not exist")
        case Some(sc) if !sc.hasProperty(CanRequest) => printLine(s"script '$scriptName' cannot be requested")
        case Some(sc) => requestInternal(minutes, sc, args.drop(2))
      }
    }
  }

  /**
    * - if the queue is empty, the script is run immediately and an unlock is added afterwards
    * - otherwise, the unlock is delayed
    */
  private def requestInternal(minutes: Int, script: Script, args: Vector[String]): SC[Unit] = for {
    time <- nowTime()
    state <- getState()
    _ <- state |> indexOfUnlock match {
      case -1 => runScriptImmediatelyAndUnlockAfterMinutes(time, script, args, minutes)
      case index => replaceUnlockWithScriptAndAddUnlockAfterMinutes(state, script, args, minutes, index)
    }
  } yield Unit

  private def runScriptImmediatelyAndUnlockAfterMinutes(time: LocalDateTime, script: Script, args: Vector[String], minutes: Int): SC[Unit] = steps(
    printLine(s"script '${script.name}' will run immediately"),
    addItemsToQueue(
      FutureAction(time, script, args),
      FutureAction(time.plusMinutes(minutes), Unlock)
    )
  )

  private def replaceUnlockWithScriptAndAddUnlockAfterMinutes(
    state: MonikaState,
    script: Script,
    args: Vector[String],
    minutes: Int,
    index: Int
  ): SC[Unit] = {
    val oldScriptAction = state.queue(index)
    val at = oldScriptAction.at
    val scriptAction = FutureAction(at, script, args)
    val unlockAction = FutureAction(at.plusMinutes(minutes), Unlock)
    val newQueue = state.queue |> removeAt(index) |> addItems(scriptAction, unlockAction)
    steps(
      printLine(s"script '${script.name}' will run at ${at.format(DefaultFormatter)}"),
      printLine(s"unlock moved to ${unlockAction.at.format(DefaultFormatter)}"),
      setState(state.copy(queue = newQueue))
    )
  }

  private def indexOfUnlock(state: MonikaState): Int = {
    state.queue.indexWhere(_.script == Unlock)
  }

  private def addItemsToQueue(items: FutureAction*): SC[Unit] = {
    transformState(state => state.copy(queue = state.queue |> addItems(items:_*)))
  }

}
