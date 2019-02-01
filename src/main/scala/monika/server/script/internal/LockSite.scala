package monika.server.script.internal

import monika.Primitives.Filename
import monika.server.proxy.URLFilter
import monika.server.script.Script
import monika.server.script.property.{Requestable, Internal}

/**
  * - locks onto the specified website for a fixed amount of time
  * - HTTP pages will be intercepted unless starting with the provided prefix
  * - will call "unlock" after time has expired
  */
object LockSite extends Script(Internal, Requestable) {

  override def run(args: Vector[String]): IOS[Unit] = {
    if (args.size != 1) {
      printLine("usage: lock-site <sites>")
    } else {
      val sites = args(0) |> toSet
      lockSiteInternal(sites)
    }
  }

  private def lockSiteInternal(sites: Set[String]): IOS[Unit] = steps(
    clearAllRestrictions(),
    setFilter(URLFilter(sites, Set.empty)),
    setAsNonRoot(),
    restrictProgramsExcept(Vector("google-chrome", "firefox").map(Filename)),
    restrictProjectsExcept(Vector.empty),
    printLine(s"locked onto ${sites.size} sites")
  )

}
