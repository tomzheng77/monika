package monika.server.script

import java.time.LocalDateTime

import monika.server.Structs.{Action, MonikaState}
import monika.server.UseDateTime
import monika.server.script.internal.{Brick, Freedom, LockProfile, Unlock}
import monika.server.script.property.{Mainline, Requestable}

import scala.language.implicitConversions

object RequestBetween extends Script with UseDateTime {

  override def run(args: Vector[String]): IOS[Unit] = for {
    startDate  ← require(parseDate(args(0)).get)(pass)("start-date is invalid")
    startTime  ← require(parseTime(args(1)).get)(pass)("start-time is invalid")
    endDate    ← require(parseDate(args(2)).get)(pass)("end-date is invalid")
    endTime    ← require(parseTime(args(3)).get)(pass)("end-time is invalid")
    scriptName ← require(args(4).trim)(notEmpty)("script cannot be empty")
    start         = LocalDateTime.of(startDate, startTime)
    end           = LocalDateTime.of(endDate, endTime)
    remainingArgs = args.drop(5)
  } yield {
    Script.allScriptsByName.get(scriptName) match {
      case None => printLine(s"script '$scriptName' does not exist")
      case Some(sc) if !sc.hasProperty(Requestable) => printLine(s"script '$scriptName' cannot be requested")
      case Some(sc) => transformState(trn(start, end)(sc, remainingArgs))
    }
  }

  private def isQueue(script: Script) = {
    script.hasProperty(Mainline)
    val queue = Set(Brick, Freedom, LockProfile, Unlock)
  }

  private def trn(
    from: LocalDateTime,
    to: LocalDateTime)
    (script: Script, args: Vector[String])
    (state: MonikaState): MonikaState = {

    val mainline: List[Action] = state.queue.filter(a ⇒ a.script.hasProperty(Mainline)).toList
    val notMainline: List[Action] = state.queue.filter(a ⇒ a.script.hasProperty(Mainline)).toList

    null
  }

}
