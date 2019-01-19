package monika.server.script.internal

import monika.Primitives.FileName
import monika.server.proxy.HTMLPrefixFilter
import monika.server.script.Script
import monika.server.script.property.Internal

import scala.util.Try

/**
  * - locks onto the specified website for a fixed amount of time
  * - HTTP pages will be intercepted unless starting with the provided prefix
  * - will call "unlock" after time has expired
  */
object LockSite extends Script(Internal) {

  override def run(args: Vector[String]): SC[Unit] = {
    if (args.size != 2) {
      printLine("usage: lock-site <sites> <minutes>")
    } else if (Try(args(1).toInt).filter(_ > 0).isFailure) {
      printLine(s"minutes must be a positive integer")
    } else {
      val sites = args(0).split(',').toSet
      val minutes = args(1).toInt
      lockSiteInternal(sites, minutes)
    }
  }

  private def lockSiteInternal(sites: Set[String], minutes: Int): SC[Unit] = for {
    time <- nowTime()
    _ <- sequence(Vector(
      setNewProxy(HTMLPrefixFilter(sites)),
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
