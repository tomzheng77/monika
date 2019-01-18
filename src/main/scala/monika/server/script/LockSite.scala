package monika.server.script

import java.time.LocalDateTime

import monika.server.Structs.FutureAction
import monika.server.proxy.HTMLPrefixFilter

import scala.util.Try

/**
  * - locks onto the specified website for a fixed amount of time
  * - HTTP pages will be intercepted unless starting with the provided prefix
  * - will call "unlock" after time has expired
  */
object LockSite extends Script with RequireRoot {

  override def run(args: Vector[String]): SC[Unit] = (api: ScriptAPI) => {
    if (args.size != 2) {
      api.println("usage: lock-site <site> <minutes>")
    } else if (Try(args(1).toInt).filter(_ > 0).isFailure) {
      api.println(s"minutes must be a positive integer")
    } else {
      val site = args(0)
      val minutes = args(1).toInt
      api.restartProxy(HTMLPrefixFilter(Set(site)))
      val nowTime = LocalDateTime.now()
      FutureAction(nowTime.plusMinutes(minutes), Unlock)
      api.println(s"locked onto site for $minutes minutes")
    }
  }

}
