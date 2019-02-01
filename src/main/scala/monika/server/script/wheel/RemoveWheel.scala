package monika.server.script.wheel

import monika.server.UseDateTime
import monika.server.script.Script
import monika.server.script.property.RootOnly

object RemoveWheel extends Script(RootOnly) with UseDateTime {

  override def run(args: Vector[String]): IOS[Unit] = steps(
    removeFromWheelGroup(),
    printLine("the user has been removed from the wheel group")
  )

}
