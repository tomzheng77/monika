package monika.server.script

import monika.server.proxy.{ProxyServer, TransparentFilter}
import monika.server.script.library.RestrictionOps

object Unlock extends Script with RequireRoot with RestrictionOps {

  override def run(args: Vector[String]): SC[Unit] = (api: ScriptAPI) => {
    ProxyServer.startOrRestart(TransparentFilter)
    api.transaction(state => (state.copy(filter = TransparentFilter), Unit))
    clearAllRestrictions()
    api.println("unlock success")
  }

}
