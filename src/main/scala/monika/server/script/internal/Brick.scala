package monika.server.script.internal

import monika.server.script.Script
import monika.server.script.library.RestrictionOps
import monika.server.script.property.Internal

import scala.util.Try

object Brick extends Script(Internal) with RestrictionOps {

  override def run(args: Vector[String]): SC[Unit] = (api: ScriptAPI) => {
    Try(args.head.toInt).toOption match {
      case None => api printLine "usage: brick <minutes>"
      case Some(m) if m <= 0 => api printLine "minutes must be greater than zero"
      case Some(m) => brickFor(m)(api); api printLine "bricked successfully"
    }
  }

  private def brickFor(minutes: Int): SC[Unit] = (api: ScriptAPI) => {
    val now = api.nowTime()
    val timeToUnlock = now.plusMinutes(minutes).withSecond(0).withNano(0)
    restrictLogin()(api)
    api.enqueue(timeToUnlock, Unlock)
  }

}
