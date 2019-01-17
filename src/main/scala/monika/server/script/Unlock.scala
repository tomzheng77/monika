package monika.server.script

import java.io.PrintWriter

import monika.server.{Hibernate, LittleProxy, Restrictions}

object Unlock extends Script with RequireRoot {

  override def run(args: Vector[String], out: PrintWriter): Unit = {
    LittleProxy.startOrRestart(LittleProxy.Transparent)
    Hibernate.transaction(state => (state.copy(proxy = LittleProxy.Transparent), Unit))
    Restrictions.clearAllRestrictions()
    out.write("unlock success")
  }

}
