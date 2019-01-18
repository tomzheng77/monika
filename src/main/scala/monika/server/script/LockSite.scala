package monika.server.script

import monika.server.proxy.HTMLPrefixFilter
import monika.server.script.library.RestrictionOps

import scala.util.Try
import monika.Primitives._

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

  private def lockSiteInternal(site: String, minutes: Int): SC[Unit] = for {
    time <- nowTime()
    _ <- sequence(Vector(
      setNewProxy(HTMLPrefixFilter(Set(site))),
      removeFromWheelGroup(),
      restrictProgramsExcept(Vector("google-chrome", "firefox").map(FileName)),
      restrictProjectsExcept(Vector.empty),
      enqueue(time.plusSeconds(10), ForceOut),
      enqueue(time.plusMinutes(minutes), Unlock),
      setAsNonRoot(),
      printLine(s"locked onto site for $minutes minutes")
    ))
  } yield {}

}
