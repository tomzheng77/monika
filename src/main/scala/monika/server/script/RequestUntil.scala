package monika.server.script

import java.time.LocalDateTime

import monika.server.Structs.Action
import monika.server.UseDateTime
import monika.server.script.internal.Unlock
import monika.server.script.property.Requestable

object RequestUntil extends Script with UseDateTime {

  override def run(args: Vector[String]): IOS[Unit] = {
    if (args.size < 3) printLine("usage: request-until <date> <time> <script> <args..>")
    else if (parseDate(args(0)).isFailure) printLine("date format is invalid")
    else if (parseTime(args(1)).isFailure) printLine("time format is invalid")
    else if (args(2).trim.isEmpty) printLine("script cannot be empty")
    else {
      val date = parseDate(args(0)).get
      val time = parseTime(args(1)).get
      val dateAndTime = LocalDateTime.of(date, time)
      val scriptName = args(2).trim
      val remainingArgs = args.drop(3)
      Script.allScriptsByName.get(scriptName) match {
        case None => printLine(s"script '$scriptName' does not exist")
        case Some(sc) if !sc.hasProperty(Requestable) => printLine(s"script '$scriptName' cannot be requested")
        case Some(sc) => requestUntilInternal(dateAndTime, sc, remainingArgs)
      }
    }
  }

  private def requestUntilInternal(untilTime: LocalDateTime, script: Script, args: Vector[String]): IOS[Unit] = {
    findScriptInQueue(Unlock).flatMap {
      case None =>
        branch(
          nowTime().map(untilTime.isAfter),
          steps(
            printLine(s"script '${script.name}' will run immediately"),
            addActionToQueueNow(script, args),
            addActionToQueue(untilTime, Unlock)
          ),
          printLine("until must be after now")
        )
      case Some((Action(at, _, _), index)) => {
        if (untilTime.isAfter(at)) steps(
          printLine(s"script '${script.name}' will run at ${at.format()}"),
          printLine(s"unlock moved to ${untilTime.format()}"),
          removeActionFromQueue(index),
          addActionToQueue(at, script, args),
          addActionToQueue(untilTime, Unlock)
        ) else {
          printLine(s"until must be after ${at.format()}")
        }
      }
    }
  }

}
