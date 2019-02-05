package monika.server.script.internal

import monika.server.proxy.URLFilter
import monika.server.script.Script
import monika.server.script.property.{Internal, Requestable}

object RejectSite extends Script(Internal, Requestable) {

  override def run(args: Vector[String]): IOS[Unit] = {
    if (args.size != 1) {
      printLine("usage: reject-site <sites>")
    } else {
      val sites = args(0) |> toSet
      rejectSiteInternal(sites)
    }
  }

  private def rejectSiteInternal(sites: Set[String]): IOS[Unit] = steps(
    clearAllRestrictions(),
    setFilter(URLFilter(Set("/.*/"), sites)),
    setAsNonRoot(),
    closeAllBrowsers(),
    restrictProjectsExcept(exceptBrowsers = true),
    printLine(s"rejected ${sites.size} sites")
  )

}
