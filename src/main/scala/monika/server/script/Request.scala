package monika.server.script

import monika.server.Structs.Action
import monika.server.UseDateTime
import monika.server.script.internal.Unlock
import monika.server.script.property.Requestable

import scala.util.Try

object Request extends Script with UseDateTime {

  override def run(args: Vector[String]): IOS[Unit] = {
    if (args.size < 2) printLine("usage: request <minutes> <script> <args..>")
    else if (Try(args(0).toInt).filter(_ > 0).isFailure) printLine("minutes must be a positive integer")
    else if (args(1).trim.isEmpty) printLine("script cannot be empty")
    else {
      val minutes = args(0).toInt
      val scriptName = args(1).trim
      Script.allScriptsByName.get(scriptName) match {
        case None => printLine(s"script '$scriptName' does not exist")
        case Some(sc) if !sc.hasProperty(Requestable) => printLine(s"script '$scriptName' cannot be requested")
        case Some(sc) => requestInternal(minutes, sc, args.drop(2))
      }
    }
  }

  private def requestInternal(minutes: Int, script: Script, args: Vector[String]): IOS[Unit] = {
    findScriptInQueue(Unlock).flatMap {
      case None => steps(
        printLine(s"script '${script.name}' will run immediately"),
        addActionToQueueNow(script, args),
        addActionToQueueAfter(minutes)(Unlock)
      )
      case Some((Action(at, _, _), index)) => steps(
        printLine(s"script '${script.name}' will run at ${at.format()}"),
        printLine(s"unlock moved to ${at.plusMinutes(minutes).format()}"),
        removeActionFromQueue(index),
        addActionToQueue(at, script, args),
        addActionToQueue(at.plusMinutes(minutes), Unlock)
      )
    }
  }

}
