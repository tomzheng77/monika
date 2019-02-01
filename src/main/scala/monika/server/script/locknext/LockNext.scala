package monika.server.script.locknext

import monika.server.script.Script
import monika.server.script.property.RootOnly
import monika.server.subprocess.Commands.passwd

object LockNext extends Script(RootOnly) {
  override def run(args: Vector[String]): SC[Unit] = SC(api => {
    api.call(passwd, "-l", "unlocker")
    api.transaction(state => {
      state.passwords.apply()
      null
    })
  })
}
