package monika.server.script.internal

import monika.Primitives.FileName
import monika.server.proxy.URLFilter
import monika.server.script.Script
import monika.server.script.property.{CanRequest, Internal}

object RejectSite extends Script(Internal, CanRequest) {

  override def run(args: Vector[String]): SC[Unit] = {
    if (args.size != 1) {
      printLine("usage: reject-site <sites>")
    } else {
      val sites = args(0) |> toSet
      rejectSiteInternal(sites)
    }
  }

  private def rejectSiteInternal(sites: Set[String]): SC[Unit] = steps(
    clearAllRestrictions(),
    setNewFilter(URLFilter(Set("/.*/"), sites)),
    setAsNonRoot(),
    restrictProgramsExcept(Vector("google-chrome", "firefox").map(FileName)),
    restrictProjectsExcept(Vector.empty),
    printLine(s"rejected ${sites.size} sites"),
    enqueueNextStep(ForceOut)
  )

}
