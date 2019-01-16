package monika.server.signal

import monika.server.UserControl

object Unlock extends Signal {
  override def run(args: Vector[String]): String = {
    UserControl.clearAllRestrictions()
    "unlock success"
  }
}
