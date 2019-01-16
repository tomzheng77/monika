package monika.server.signal

import monika.server.Structs.{Action, FutureAction}

/**
  * @param message to return to the user who called the signal
  * @param actions to perform immediately
  * @param futureActions to perform at a future time
  */
case class SignalResult(
  message: String,
  actions: Vector[Action] = Vector.empty,
  futureActions: Set[FutureAction] = Set.empty
)
