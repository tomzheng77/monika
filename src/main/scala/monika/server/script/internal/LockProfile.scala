package monika.server.script.internal

import monika.Primitives.Filename
import monika.server.proxy.{TransparentFilter, URLFilter}
import monika.server.script.Script
import monika.server.script.property.{Internal, Mainline, Requestable}

object LockProfile extends Script(Internal, Requestable, Mainline) {

  override def run(args: Vector[String]): IOS[Unit] = {
    if (args.size != 3) {
      printLine("usage: lock-profile <url-allow> <url-reject> <projects>")
    } else {
      val urlAllow = args(0) |> toSet
      val urlReject = args(1) |> toSet
      val projects = args(2) |> toSet
      lockProfileInternal(urlAllow, urlReject, projects)
    }
  }

  private def lockProfileInternal(
    urlAllow: Set[String],
    urlReject: Set[String],
    projects: Set[String]
  ): IOS[Unit] = steps(
    clearAllRestrictions(),
    if (urlAllow("<all>") && urlReject.isEmpty) setFilter(TransparentFilter)
    else setFilter(URLFilter(urlAllow, urlReject)),
    closeAllBrowsers(),
    restrictProjectsExcept(projects.map(Filename).toVector)
  )

}
