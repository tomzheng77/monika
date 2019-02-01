package monika.server.script

import monika.server.Structs.{FutureAction, MonikaState}
import monika.server.UseDateTime
import monika.server.script.internal.Unlock
import monika.server.script.property.CanRequest

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
        case Some(sc) if !sc.hasProperty(CanRequest) => printLine(s"script '$scriptName' cannot be requested")
        case Some(sc) => requestInternal(minutes, sc, args.drop(2))
      }
    }
  }

  /**
    * - if the queue is empty, the script is run immediately and an unlock is added afterwards
    * - otherwise, the unlock is delayed
    */
  private def requestInternal(minutes: Int, script: Script, args: Vector[String]): IOS[Unit] = for {
    time <- nowTime()
    _ <- findScriptInQueue(Unlock).flatMap {
      case None => steps(
        printLine(s"script '${script.name}' will run immediately"),
        addActionToQueue(time, script, args),
        addActionToQueue(time.plusMinutes(minutes), Unlock)
      )
      case Some((FutureAction(at, _, _), index)) => steps(
        printLine(s"script '${script.name}' will run at ${at.format(DefaultFormatter)}"),
        printLine(s"unlock moved to ${at.format(DefaultFormatter)}"),
        removeActionFromQueue(index),
        addActionToQueue(at, script, args),
        addActionToQueue(at.plusMinutes(minutes), Unlock)
      )
    }
  } yield Unit

}
