package monika.server.script.internal

import monika.server.proxy.TransparentFilter
import monika.server.script.Script
import monika.server.script.property.{CanRequest, Internal}

object Freedom extends Script(Internal, CanRequest) {

  override def run(args: Vector[String]): IOS[Unit] = steps(
    clearAllRestrictions(),
    setNewFilter(TransparentFilter),
    setAsNonRoot()
  )

}
