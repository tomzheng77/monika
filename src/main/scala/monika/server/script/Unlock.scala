package monika.server.script

import monika.server.proxy.TransparentFilter
import monika.server.script.library.RestrictionOps

object Unlock extends Script with RequireRoot with RestrictionOps {

  override def run(args: Vector[String]): SC[Unit] = (api: ScriptAPI) => {
    api.restartProxy(TransparentFilter)
    api.transaction(state => (state.copy(filter = TransparentFilter, root = true), Unit))
    clearAllRestrictions()
    api.println("unlock success")
  }

}
