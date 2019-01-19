package monika.server.script.internal

import monika.server.proxy.TransparentFilter
import monika.server.script.Script
import monika.server.script.property.Internal

object Unlock extends Script(Internal) {

  override def run(args: Vector[String]): SC[Unit] = (api: ScriptAPI) => {
    api.restartProxy(TransparentFilter)
    api.transaction(state => (state.copy(filter = TransparentFilter, root = true), Unit))
    clearAllRestrictions()(api)
    api.printLine("unlock success")
  }

}
