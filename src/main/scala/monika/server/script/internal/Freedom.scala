package monika.server.script.internal

import monika.server.proxy.TransparentFilter
import monika.server.script.Script
import monika.server.script.property.{Internal, Linear, Requestable}

object Freedom extends Script(Internal, Requestable, Linear) {

  override def run(args: Vector[String]): IOS[Unit] = steps(
    clearAllRestrictions(),
    setFilter(TransparentFilter),
    setAsNonRoot()
  )

}
