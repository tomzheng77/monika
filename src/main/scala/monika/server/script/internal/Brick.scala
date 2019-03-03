package monika.server.script.internal

import monika.server.script.Script
import monika.server.script.property.{Internal, Linear, Requestable}

object Brick extends Script(Internal, Requestable, Linear) {

  override def run(args: Vector[String]): IOS[Unit] = steps(
    clearAllRestrictions(),
    restrictLogin(),
    addActionToQueueNow(ForceOut)
  )

}
