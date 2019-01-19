package monika.server.script.internal

import monika.server.Constants.Locations
import monika.server.script.Script
import monika.server.script.property.Internal

object SetProfile extends Script(Internal) {

  override def run(args: Vector[String]): SC[Unit] = (api: ScriptAPI) => {
    val profiles = api.activeProfiles()
    lazy val name = args.head
    if (args.isEmpty) {
      api.printLine("usage: set-profile <profile>")
    } else if (!profiles.contains(name)) {
      api.printLine(s"cannot find profile $name, please check ${Locations.ProfileRoot}")
    } else {
      val profile = profiles(name)
      api.restartProxy(profile.filter)
      removeFromWheelGroup()(api)
      restrictProgramsExcept(profile.programs)(api)
      restrictProjectsExcept(profile.projects)(api)
      api.transaction(state => (state.copy(filter = profile.filter), Unit))
      api.printLine("set-profile success")
    }
  }

}
