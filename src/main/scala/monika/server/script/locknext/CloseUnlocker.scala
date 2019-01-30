package monika.server.script.locknext

import monika.server.script.Script
import monika.server.subprocess.Commands.passwd

object CloseUnlocker extends Script {
  override def run(args: Vector[String]): SC[Unit] = SC(api => {
    api.call(passwd, "-l", "unlocker")
  })
}
