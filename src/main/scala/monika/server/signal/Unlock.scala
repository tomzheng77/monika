package monika.server.signal

import monika.server.Structs.ClearAllRestrictions

object Unlock extends Script {
  override def run(args: Vector[String]): SignalResult = {
    SignalResult(message = "unlock success", actions = Vector(ClearAllRestrictions))
  }
}
