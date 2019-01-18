package monika.server.script

import java.io.PrintWriter

import monika.server.proxy.{ProxyServer, TransparentFilter}
import monika.server.{Hibernate, Restrictions}

object Unlock extends Script with RequireRoot {

  override def run(args: Vector[String], out: PrintWriter): Unit = {
    ProxyServer.startOrRestart(TransparentFilter)
    Hibernate.transaction(state => (state.copy(filter = TransparentFilter), Unit))
    Restrictions.clearAllRestrictions()
    out.write("unlock success")
  }

}
