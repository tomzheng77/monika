package monika.server.script

import java.time.LocalDateTime

import monika.server.script.library.RestrictionOps

import scala.util.Try

object Brick extends Script with RequireRoot with RestrictionOps {

  override def run(args: Vector[String]): SC[Unit] = (api: ScriptAPI) => {
    Try(args.head.toInt).toOption match {
      case None => api println "usage: brick <minutes>"
      case Some(m) if m <= 0 => api println "minutes must be greater than zero"
      case Some(m) => brickFor(m)(api); api println "bricked successfully"
    }
  }

  private def brickFor(minutes: Int): SC[Unit] = (api: ScriptAPI) => {
    val now = api.nowTime()
    val timeToUnlock = now.plusMinutes(minutes).withSecond(0).withNano(0)
    restrictLogin()(api)
    api.enqueue(timeToUnlock, Unlock)
  }

}
