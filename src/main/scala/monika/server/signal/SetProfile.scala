package monika.server.signal

import monika.server.Configuration
import monika.server.Constants.Locations
import monika.server.Structs.RestrictProfile

object SetProfile extends Signal {
  override def run(args: Vector[String]): SignalResult = {
    val profiles = Configuration.readProfileDefinitions()
    lazy val name = args.head
    if (args.isEmpty) {
      SignalResult("usage: set-profile <profile>")
    } else if (!profiles.contains(name)) {
      SignalResult(s"cannot find profile $name, please check ${Locations.ProfileRoot}")
    } else {
      val profile = profiles(name)
      SignalResult("set-profile success", actions = Vector(RestrictProfile(profile)))
    }
  }
}
