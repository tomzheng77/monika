package monika.server.signal

import monika.server.Structs.ClearAllRestrictions

object Unlock extends Signal {
  override def run(args: Vector[String]): SignalResult = {
    SignalResult(message = "unlock success", actions = Vector(ClearAllRestrictions))
  }
}
