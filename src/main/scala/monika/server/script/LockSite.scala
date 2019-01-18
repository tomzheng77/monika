package monika.server.script

import monika.server.proxy.HTMLPrefixFilter
import monika.server.script.library.RestrictionOps

import scala.util.Try

/**
  * - locks onto the specified website for a fixed amount of time
  * - HTTP pages will be intercepted unless starting with the provided prefix
  * - will call "unlock" after time has expired
  */
object LockSite extends Script with RequireRoot with RestrictionOps {

  override def run(args: Vector[String]): SC[Unit] = {
    if (args.size != 2) {
      printLine("usage: lock-site <site> <minutes>")
    } else if (Try(args(1).toInt).filter(_ > 0).isFailure) {
      printLine(s"minutes must be a positive integer")
    } else {
      val site = args(0)
      val minutes = args(1).toInt
      lockSiteInternal(site, minutes)
    }
  }

  private def lockSiteInternal(site: String, minutes: Int): SC[Unit] = SC(api => {
    val nowTime = api.nowTime()
    api.restartProxy(HTMLPrefixFilter(Set(site)))
    removeFromWheelGroup()(api)
    restrictProgramsExcept(Vector.empty)(api)
    restrictProjectsExcept(Vector.empty)(api)
    api.enqueue(nowTime.plusMinutes(minutes), Unlock)
    setAsNonRoot()(api)
    api.println(s"locked onto site for $minutes minutes")
  })

}
