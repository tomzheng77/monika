package monika.server.script.internal

import monika.server.script.Script
import monika.server.script.property.{Requestable, Internal}

object Brick extends Script(Internal, Requestable) {

  override def run(args: Vector[String]): IOS[Unit] = steps(
    clearAllRestrictions(),
    restrictLogin(),
    addActionToQueueAfter(1)(ForceOut)
  )

}
