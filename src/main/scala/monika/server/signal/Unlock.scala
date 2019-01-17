package monika.server.signal

import java.io.PrintWriter
import monika.server.Restrictions

object Unlock extends Script {
  override def run(args: Vector[String], out: PrintWriter): Unit = {
    Restrictions.clearAllRestrictions()
    out.write("unlock success")
  }
}
