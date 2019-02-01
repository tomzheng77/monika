package monika.server.script.wheel

import monika.server.UseDateTime
import monika.server.script.Script
import monika.server.script.property.RootOnly

object AddWheel extends Script(RootOnly) with UseDateTime {

  override def run(args: Vector[String]): SC[Unit] = steps(
    addToWheelGroup(),
    printLine("the user has been added to the wheel group")
  )

}
