package monika.server.script.locknext

import monika.server.script.Script
import monika.server.script.property.RootOnly

object LockNext extends Script(RootOnly) {
  override def run(args: Vector[String]): SC[Unit] = SC(api => {
    api.transaction()
  })
}
