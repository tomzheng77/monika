package monika.server.script

import java.time.LocalDateTime

import monika.server.UseDateTime
import monika.server.script.property.Requestable

object RequestUntilToday extends Script with UseDateTime {

  override def run(args: Vector[String]): IOS[Unit] = {
    if (args.size < 2) printLine("usage: request-until-today <time> <script> <args..>")
    else if (parseTime(args(0)).isFailure) printLine("time format is invalid")
    else if (args(1).trim.isEmpty) printLine("script cannot be empty")
    else nowTime().flatMap(now ⇒ {
      val time = parseTime(args(0)).get
      val dateAndTime = LocalDateTime.of(now.toLocalDate, time)
      val scriptName = args(1).trim
      val remainingArgs = args.drop(2)
      Script.allScriptsByName.get(scriptName) match {
        case None => printLine(s"script '$scriptName' does not exist")
        case Some(sc) if !sc.hasProperty(Requestable) => printLine(s"script '$scriptName' cannot be requested")
        case Some(sc) => RequestUntil.requestUntilInternal(dateAndTime, sc, remainingArgs)
      }
    })
  }

}
