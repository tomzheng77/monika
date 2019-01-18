package monika.server.script

import monika.server.Structs.FutureAction
import monika.server.{UseDateTime, UseScalaz}

object Status extends Script with UseScalaz with UseDateTime {

  override def run(args: Vector[String]): SC[Unit] = (api: ScriptAPI) => {
    for (script <- Script.allScripts) {
      script match {
        case _: RequireRoot => api.printLine(s"${script.name} (root)")
        case _ => api.printLine(s"${script.name}")
      }
    }
    val state = api.query()
    for (FutureAction(at, script, args) <- state.queue) {
      api.printLine(s"${at.format(DefaultFormatter)}}: ${script.name} ${args.mkString(" ")}")
    }
  }

}
