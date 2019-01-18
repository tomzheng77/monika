package monika.server.script

import monika.server.Restrictions
import monika.server.proxy.{ProxyServer, TransparentFilter}

object Unlock extends Script with RequireRoot {

  override def run(args: Vector[String]): SC[Unit] = (api: ScriptAPI) => {
    ProxyServer.startOrRestart(TransparentFilter)
    api.transaction(state => (state.copy(filter = TransparentFilter), Unit))
    Restrictions.clearAllRestrictions()
    api.println("unlock success")
  }

}
