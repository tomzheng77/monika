package monika.server.script

import monika.server.Structs.FutureAction
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
  def requestInternal(minutes: Int, script: Script, args: Vector[String]): SC[Unit] = SC(api => {
    val now = api.nowTime()
    api.transaction(state => {
      state.queue.indexWhere(act => act.script == Unlock) match {
        case -1 => {
          api.printLine(s"script '${script.name} will run immediately")
          (state.copy(queue = Vector(
            FutureAction(now, script),
            FutureAction(now.plusMinutes(minutes), Unlock)
          )), Unit)
        }
        case index => {
          val oldScriptAct = state.queue(index)
          val at = oldScriptAct.at
          val scriptAct = FutureAction(at, script, args)
          val unlockAct = FutureAction(at.plusMinutes(minutes), Unlock)
          api.printLine(s"script '${script.name}' will run at ${at.format(DefaultFormatter)}")
          api.printLine(s"unlock moved to ${unlockAct.at.format(DefaultFormatter)}")
          val newQueue = state.queue |> removeAt(index) |> addItems(scriptAct, unlockAct)
          (state.copy(queue = newQueue), Unit)
        }
      }
    })
  })

}
