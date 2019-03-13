package monika.server.script

import java.time.{LocalDateTime, ZoneOffset}

import monika.server.Structs.{Action, MonikaState}
import monika.server.UseDateTime
import monika.server.script.internal.{Freedom, Unlock}
import monika.server.script.property.{Mainline, Requestable}

import scala.annotation.tailrec
import scala.language.implicitConversions

object Request extends Script with UseDateTime {

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
      case Some(sc) => getState().map(requestBetweenInternal(start, end)(sc, remainingArgs)).flatMap[Unit] {
        case Left(errorMessage) ⇒ printLine(errorMessage)
        case Right(newState) ⇒ steps(
          setState(newState),
          printLine(s"script '$scriptName' added from ${start.format()} to ${end.format()}")
        )
      }
    }
  }

  private val Epoch = LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC)

  def requestBetweenInternal(start: LocalDateTime, end: LocalDateTime)
                            (script: Script, args: Vector[String])
                            (state: MonikaState): Either[String, MonikaState] = {

    if (!end.isAfter(start)) return Left("end must be after start")

    val previous: Action = state.previous.filter(a ⇒ a.script.hasProperty(Mainline)).getOrElse(Action(Epoch, Unlock))
    val mainline: List[Action] = state.queue.filter(a ⇒ a.script.hasProperty(Mainline)).toList
    val notMainline: List[Action] = state.queue.filterNot(a ⇒ a.script.hasProperty(Mainline)).toList

    if (start.isBefore(previous.at)) return Left(s"start must be after previous action at ${previous.at.format()}")

    val atOrBeforeStart: List[Action] = mainline
      .takeWhile(!_.at.isAfter(start))
      .map(a ⇒ if (a.script == Unlock) a.copy(script = Freedom) else a)

    val atOrAfterEnd: List[Action] = mainline.dropWhile(_.at.isBefore(end))
    val inBetween: List[Action] = mainline.slice(atOrBeforeStart.length, mainline.length - atOrAfterEnd.length)

    // check if the script intersects with a non-freedom action
    val typeAtStart = atOrBeforeStart.lastOption.getOrElse(previous)
    val isBlocked = (typeAtStart :: inBetween).exists(nonFree)
    if (isBlocked) return Left("must not overlap with non-free sessions")

    val newMainline = atOrBeforeStart ++ List(Action(start, script, args)) ++ {
      if (atOrAfterEnd.isEmpty) List(Action(end, Unlock))
      else if (atOrAfterEnd.head.at.isEqual(end)) Nil
      else List(Action(end, Freedom))
    } ++ atOrAfterEnd

    Right(state.copy(queue = (removeDuplicate(removeStacking(newMainline)).dropWhile(a ⇒ {
      a.script == previous.script && a.args == previous.args
    }) ++ notMainline).sortBy(_.at).toVector))
  }

  def nonFree(a: Action): Boolean = a.script != Unlock && a.script != Freedom

  def removeStacking(list: List[Action]): List[Action] = {
    list.groupBy(_.at).flatMap(g ⇒ {
      if (g._2.exists(nonFree)) g._2.filter(nonFree) else g._2
    }).toList
  }

  // removes any action if the one before has the same script and args
  def removeDuplicate(list: List[Action]): List[Action] = {
    @tailrec
    def loop(at: List[Action], out: List[Action]): List[Action] = at match {
      case Nil ⇒ out
      case one :: Nil ⇒ one :: out
      case one :: two :: remain ⇒ {
        if (one.script == two.script && one.args == two.args) loop(one :: remain, out)
        else loop(two :: remain, one :: out)
      }
    }
    loop(list.sortBy(_.at), Nil).reverse
  }

}
