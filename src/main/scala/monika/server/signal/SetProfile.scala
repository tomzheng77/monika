package monika.server.signal

import monika.server.Constants.Locations
import monika.server.{Configuration, LittleProxy, UserControl}

object SetProfile extends Signal {
  override def run(args: Vector[String]): String = {
    val profiles = Configuration.readProfileDefinitions()
    lazy val name = args.head
    if (args.isEmpty) {
      "usage: set-profile <profile>"
    } else if (!profiles.contains(name)) {
      s"cannot find profile $name, please check ${Locations.ProfileRoot}"
    } else {
      val profile = profiles(name)
      LittleProxy.startOrRestart(profile.proxy)
      UserControl.removeFromWheelGroup()
      UserControl.restrictProgramsExcept(profile.programs)
      UserControl.restrictProjectsExcept(profile.projects)
      "set-profile success"
    }
  }
}
