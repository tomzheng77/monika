package monika.server.signal

import java.io.PrintWriter

import monika.server.{Hibernate, LittleProxy, Restrictions}

object Unlock extends Script {

  override def run(args: Vector[String], out: PrintWriter): Unit = {
    LittleProxy.startOrRestart(LittleProxy.Transparent)
    Hibernate.transaction(state => (state.copy(proxy = LittleProxy.Transparent), Unit))
    Restrictions.clearAllRestrictions()
    out.write("unlock success")
  }

}
