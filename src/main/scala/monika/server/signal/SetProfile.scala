package monika.server.signal

import java.io.PrintWriter

import monika.server.{Configuration, Hibernate, LittleProxy, Restrictions}
import monika.server.Constants.Locations

object SetProfile extends Script {

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
