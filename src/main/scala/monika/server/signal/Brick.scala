package monika.server.signal

import java.time.{LocalDateTime, ZoneOffset}

import monika.Primitives.TimeFormat
import monika.server.Structs.{Action, MonikaState, ClearAllRestrictions}
import monika.server.{Persistence, UserControl}

import scala.util.Try

object Brick extends Signal {
  override def run(args: Vector[String]): String = {
    Try(args.head.toInt).toOption match {
      case None => "usage: brick <minutes>"
      case Some(m) if m <= 0 => "minutes must be greater than zero"
      case Some(m) => brickFor(m)
    }
  }
  private def brickFor(minutes: Int): String = {
    def addItemToQueue(state: MonikaState, time: LocalDateTime, action: Action): MonikaState = {
      state.copy(queue = (state.queue :+ ((time, action))).sortBy(_._1.toEpochSecond(ZoneOffset.UTC)))
    }
    UserControl.restrictLogin()
    Persistence.transaction(state => {
      val now = LocalDateTime.now()
      val timeToUnlock = now.plusMinutes(minutes).withSecond(0).withNano(0)
      val newState = addItemToQueue(state, timeToUnlock, ClearAllRestrictions)
      val list = newState.queue.map(item => {
        s"${item._1.format(TimeFormat)}: ${item._2}"
      }).mkString("\n")
      (newState, "successfully added to queue, queue is now:\n" + list)
    })
  }
}
