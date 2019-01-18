package monika.server.script

import java.io.PrintWriter

import monika.server.{Configuration, Hibernate, Restrictions}
import monika.server.Constants.Locations
import monika.server.proxy.ProxyServer

object SetProfile extends Script with RequireRoot {

  override def run(args: Vector[String], out: PrintWriter): Unit = {
    val profiles = Configuration.readProfileDefinitions()
    lazy val name = args.head
    if (args.isEmpty) {
      out.println("usage: set-profile <profile>")
    } else if (!profiles.contains(name)) {
      out.println(s"cannot find profile $name, please check ${Locations.ProfileRoot}")
    } else {
      val profile = profiles(name)
      ProxyServer.startOrRestart(profile.filter)
      Restrictions.removeFromWheelGroup()
      Restrictions.restrictProgramsExcept(profile.programs)
      Restrictions.restrictProjectsExcept(profile.projects)
      Hibernate.transaction(state => (state.copy(filter = profile.filter), Unit))
      out.println("set-profile success")
    }
  }

}
