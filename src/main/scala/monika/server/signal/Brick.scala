package monika.server.signal

import java.time.LocalDateTime

import monika.server.Structs._

import scala.util.Try

object Brick extends Signal {
  override def run(args: Vector[String]): SignalResult = {
    Try(args.head.toInt).toOption match {
      case None => "usage: brick <minutes>"
      case Some(m) if m <= 0 => "minutes must be greater than zero"
      case Some(m) => brickFor(m)
    }
  }
  private def brickFor(minutes: Int): SignalResult = {
    val now = LocalDateTime.now()
    val timeToUnlock = now.plusMinutes(minutes).withSecond(0).withNano(0)
    SignalResult(
      "successfully added item to queue",
      actions = Vector(DisableLogin),
      futureActions = Set(FutureAction(timeToUnlock, ClearAllRestrictions))
    )
  }
}
