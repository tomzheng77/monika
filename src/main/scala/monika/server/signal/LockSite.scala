package monika.server.signal

import java.io.PrintWriter

import monika.server.Constants.Locations
import monika.server.{Configuration, Hibernate, LittleProxy, Restrictions}

/**
  * - locks onto the specified website for a fixed amount of time
  * - HTTP pages will be intercepted unless starting with the provided prefix
  * - will call "unlock" after time has expired
  */
object LockSite extends Script {

  override def run(args: Vector[String], out: PrintWriter): Unit = {
    val profiles = Configuration.readProfileDefinitions()
    lazy val name = args.head
    if (args.isEmpty) {
      out.println("usage: set-profile <profile>")
    } else if (!profiles.contains(name)) {
      out.println(s"cannot find profile $name, please check ${Locations.ProfileRoot}")
    } else {
      val profile = profiles(name)
      LittleProxy.startOrRestart(profile.proxy)
      Restrictions.removeFromWheelGroup()
      Restrictions.restrictProgramsExcept(profile.programs)
      Restrictions.restrictProjectsExcept(profile.projects)
      Hibernate.transaction(state => (state.copy(proxy = profile.proxy), Unit))
      out.println("set-profile success")
    }
  }

}
