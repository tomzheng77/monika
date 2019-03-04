package monika.server.script

import java.time.{LocalDateTime, ZoneOffset}

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

  private val Epoch = LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC)

  private def trn(start: LocalDateTime, end: LocalDateTime)
    (script: Script, args: Vector[String])
    (state: MonikaState): MonikaState = {

    if (!end.isAfter(start)) return state

    val previous: Action = state.previous.filter(a ⇒ a.script.hasProperty(Mainline)).getOrElse(Action(Epoch, Unlock))
    val mainline: List[Action] = state.queue.filter(a ⇒ a.script.hasProperty(Mainline)).toList
    val notMainline: List[Action] = state.queue.filter(a ⇒ a.script.hasProperty(Mainline)).toList

    val beforeStart = mainline.takeWhile(_.at.isBefore(start))
    val atOrAfterEnd = mainline.dropWhile(_.at.isBefore(end))
    val inBetween = mainline.slice(beforeStart.length, mainline.length - atOrAfterEnd.length)

    // check if the script intersects with a non-freedom action
    val typeAtStart = beforeStart.lastOption.getOrElse(previous)
    val isBlocked = (typeAtStart :: inBetween).exists(a ⇒ a.script != Unlock && a.script != Freedom)
    if (isBlocked) return state

    val newMainline = beforeStart ++ List(Action(start, script, args)) ++ {
      if (atOrAfterEnd.isEmpty) List(Action(end, Unlock, args))
      else if (atOrAfterEnd.head.at.isEqual(end)) Nil
      else List(Action(end, Freedom, args))
    } ++ atOrAfterEnd

    state.copy(queue = (newMainline ++ notMainline).sortBy(_.at).toVector)
  }

}
