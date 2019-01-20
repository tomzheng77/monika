package monika.server.script

import monika.server.Structs.FutureAction
import monika.server.{UseDateTime, UseScalaz}

object Status extends Script with UseScalaz with UseDateTime {

  override def run(args: Vector[String]): SC[Unit] = (api: ScriptAPI) => {
    api.printLine("========== [Commands] ==========")
    for (script <- Script.allScripts) {
      if (script.props.isEmpty) api.printLine(s"- ${script.name}")
      else api.printLine(s"- ${script.name} (props: ${script.props.map(_.name).mkString(", ")})")
    }
    api.printLine("")
    api.printLine("========== [Queue] ==========")
    val state = api.query()
    for (FutureAction(at, script, args) <- state.queue) {
      api.printLine(s"- ${at.format(DefaultFormatter)}: ${script.name} ${args.mkString(" ")}")
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
