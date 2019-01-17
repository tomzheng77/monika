package monika.server.script

import java.io.PrintWriter
import java.time.LocalDateTime

import monika.server.Restrictions
import monika.server.Structs._

import scala.util.Try

object Brick extends Script {

  override def run(args: Vector[String], out: PrintWriter): Unit = {
    Try(args.head.toInt).toOption match {
      case None => out println "usage: brick <minutes>"
      case Some(m) if m <= 0 => out println "minutes must be greater than zero"
      case Some(m) => brickFor(m); out println "bricked successfully"
    }
  }

  private def brickFor(minutes: Int): Unit = {
    val now = LocalDateTime.now()
    val timeToUnlock = now.plusMinutes(minutes).withSecond(0).withNano(0)
    Restrictions.restrictLogin()
    ScriptServer.enqueue(FutureAction(timeToUnlock, Unlock))
  }

}
