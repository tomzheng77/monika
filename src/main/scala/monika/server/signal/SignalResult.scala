package monika.server.signal

import monika.server.Structs.{Action, FutureAction}

case class SignalResult(message: String, actions: Vector[Action], futureActions: Set[FutureAction])
