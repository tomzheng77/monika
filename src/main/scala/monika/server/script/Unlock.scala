package monika.server.script

import monika.server.proxy.{ProxyServer, TransparentFilter}
import monika.server.script.library.Restrictions

object Unlock extends Script with RequireRoot {

  override def run(args: Vector[String]): SC[Unit] = (api: ScriptAPI) => {
    ProxyServer.startOrRestart(TransparentFilter)
    api.transaction(state => (state.copy(filter = TransparentFilter), Unit))
    Restrictions.clearAllRestrictions()
    api.println("unlock success")
  }

}
