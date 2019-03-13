package monika.server.script.internal

import monika.server.proxy.TransparentFilter
import monika.server.script.Script
import monika.server.script.property.{Internal, Mainline}

object Unlock extends Script(Internal, Mainline) {

  override def run(args: Vector[String]): IOS[Unit] = steps(
    setFilter(TransparentFilter),
    clearAllRestrictions(),
    printLine("unlock success")
  )

}
