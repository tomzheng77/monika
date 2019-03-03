package monika.server.script

import monika.server.Structs.Action
import monika.server.{UseDateTime, UseScalaz}

object Status extends Script with UseScalaz with UseDateTime {

  override def run(args: Vector[String]): IOS[Unit] = (api: ScriptAPI) => {
    api.printLine("========== [Commands] ==========")
    val maxNameLength = Script.allScripts.map(_.name.length).max
    for (script <- Script.allScripts.sortBy(_.name)) {
      val name = script.name
      val padLength = maxNameLength - name.length
      val pad = Vector.fill(padLength)(' ').mkString
      if (script.props.isEmpty) api.printLine(s"- ${script.name}$pad")
      else {
        val sortedProps = script.props.map(_.name).sorted
        api.printLine(s"- ${script.name}$pad (${sortedProps.mkString(", ")})")
      }
    }
    api.printLine("")
    api.printLine("========== [Queue] ==========")
    val state = api.getState()
    for (Action(at, script, args) <- state.queue) {
      api.printLine(s"- ${at.format()}: ${script.name} ${args.mkString(" ")}")
    }

    api.printLine("")
    api.printLine("========== [Account] ==========")
    if (state.root) {
      api.printLine("- You have root access")
    } else {
      api.printLine("- You do not have root access")
    }
    api.printLine(s"- The proxy is set to ${state.filter.getClass.getSimpleName}")
  }

}
