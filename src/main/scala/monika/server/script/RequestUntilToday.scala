package monika.server.script

import java.time.{LocalDate, LocalDateTime, LocalTime}

import monika.server.Structs.FutureAction
import monika.server.UseDateTime
import monika.server.script.internal.Unlock
import monika.server.script.property.Requestable

import scala.util.Try

object RequestUntilToday extends Script with UseDateTime {

  override def run(args: Vector[String]): IOS[Unit] = {
    if (args.size < 2) printLine("usage: request-until-today <time> <script> <args..>")
    else if (Try(LocalTime.parse(args(0), DefaultTimeFormatter)).isFailure) printLine("time format is invalid")
    else if (args(1).trim.isEmpty) printLine("script cannot be empty")
    else {
      nowTime().flatMap(now ⇒ {
        val time = LocalTime.parse(args(1), DefaultTimeFormatter)
        val dateAndTime = LocalDateTime.of(now.toLocalDate, time)
        val scriptName = args(2).trim
        Script.allScriptsByName.get(scriptName) match {
          case None => printLine(s"script '$scriptName' does not exist")
          case Some(sc) if !sc.hasProperty(Requestable) => printLine(s"script '$scriptName' cannot be requested")
          case Some(sc) => requestInternal(dateAndTime, sc, args.drop(2))
        }
      })
    }
  }

  private def requestInternal(untilTime: LocalDateTime, script: Script, args: Vector[String]): IOS[Unit] = {
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
      case Some((FutureAction(at, _, _), index)) => {
        if (untilTime.isAfter(at)) steps(
          printLine(s"script '${script.name}' will run at ${at.format(DefaultFormatter)}"),
          printLine(s"unlock moved to ${untilTime.format(DefaultFormatter)}"),
          removeActionFromQueue(index),
          addActionToQueue(at, script, args),
          addActionToQueue(untilTime, Unlock)
        ) else {
          printLine(s"until must be after ${at.format(DefaultDateFormatter)}")
        }
      }
    }
  }

}
