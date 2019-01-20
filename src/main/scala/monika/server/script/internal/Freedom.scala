package monika.server.script.internal

import monika.server.script.Script
import monika.server.script.property.{CanRequest, Internal}

object Freedom extends Script(Internal, CanRequest) {

  override def run(args: Vector[String]): SC[Unit] = steps(
    clearAllRestrictions(),
    removeFromWheelGroup(),
    setAsNonRoot(),
    enqueueNextStep(ForceOut)
  )

}
