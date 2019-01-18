package monika.server.script

import monika.server.Constants.Locations
import monika.server.proxy.ProxyServer
import monika.server.{Configuration, Hibernate, Restrictions}

object SetProfile extends Script with RequireRoot {

  override def run(args: Vector[String]): SC[Unit] = (api: ScriptAPI) => {
    val profiles = Configuration.readProfileDefinitions()
    lazy val name = args.head
    if (args.isEmpty) {
      api.println("usage: set-profile <profile>")
    } else if (!profiles.contains(name)) {
      api.println(s"cannot find profile $name, please check ${Locations.ProfileRoot}")
    } else {
      val profile = profiles(name)
      ProxyServer.startOrRestart(profile.filter)
      Restrictions.removeFromWheelGroup()
      Restrictions.restrictProgramsExcept(profile.programs)
      Restrictions.restrictProjectsExcept(profile.projects)
      Hibernate.transaction(state => (state.copy(filter = profile.filter), Unit))
      api.println("set-profile success")
    }
  }

}
