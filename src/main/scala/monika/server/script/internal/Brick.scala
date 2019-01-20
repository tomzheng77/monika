package monika.server.script.internal

import monika.server.script.Script
import monika.server.script.property.{CanRequest, Internal}

object Brick extends Script(Internal, CanRequest) {

  override def run(args: Vector[String]): SC[Unit] = steps(
    clearAllRestrictions(),
    restrictLogin(),
    enqueueNextStep(ForceOut)
  )

}
