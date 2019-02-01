package monika.server.script.internal

import monika.Primitives.Filename
import monika.server.proxy.URLFilter
import monika.server.script.Script
import monika.server.script.property.{Requestable, Internal}

object LockProfile extends Script(Internal, Requestable) {

  override def run(args: Vector[String]): IOS[Unit] = {
    if (args.size != 4) {
      printLine("usage: lock-profile <url-allow> <url-reject> <programs> <projects>")
    } else {
      val urlAllow = args(0) |> toSet
      val urlReject = args(1) |> toSet
      val programs = args(2) |> toSet
      val projects = args(3) |> toSet
      lockProfileInternal(urlAllow, urlReject, programs, projects)
    }
  }

  private def lockProfileInternal(
    urlAllow: Set[String],
    urlReject: Set[String],
    programs: Set[String],
    projects: Set[String]
  ): IOS[Unit] = steps(
    clearAllRestrictions(),
    setFilter(URLFilter(urlAllow, urlReject)),
    setAsNonRoot(),
    restrictProgramsExcept(programs.map(Filename).toVector),
    restrictProjectsExcept(projects.map(Filename).toVector)
  )

}
