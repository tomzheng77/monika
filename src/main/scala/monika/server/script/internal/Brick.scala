package monika.server.script.internal

import monika.server.script.Script
import monika.server.script.property.{Internal, Requestable}

object Brick extends QScript(Internal, Requestable) {

  override def run(args: Vector[String]): IOS[Unit] = steps(
    clearAllRestrictions(),
    restrictLogin(),
    addActionToQueueNow(ForceOut)
  )

}
