package monika.server.script.internal

import monika.Primitives.FileName
import monika.server.proxy.HTMLPrefixFilter
import monika.server.script.Script
import monika.server.script.property.{CanRequest, Internal}

/**
  * - locks onto the specified website for a fixed amount of time
  * - HTTP pages will be intercepted unless starting with the provided prefix
  * - will call "unlock" after time has expired
  */
object LockSite extends Script(Internal, CanRequest) {

  override def run(args: Vector[String]): SC[Unit] = {
    if (args.size != 1) {
      printLine("usage: lock-site <sites>")
    } else {
      val sites = args(0).split(',').toSet
      lockSiteInternal(sites)
    }
  }

  private def lockSiteInternal(sites: Set[String]): SC[Unit] = steps(
    clearAllRestrictions(),
    setNewProxy(HTMLPrefixFilter(sites)),
    setAsNonRoot(),
    restrictProgramsExcept(Vector("google-chrome", "firefox").map(FileName)),
    restrictProjectsExcept(Vector.empty),
    printLine(s"locked onto ${sites.size} sites")
  )

}
