package monika.server.script.wheel

import monika.server.UseDateTime
import monika.server.script.Script

object RemoveWheel extends Script with UseDateTime {

  override def run(args: Vector[String]): SC[Unit] = steps(
    removeFromWheelGroup(),
    printLine("the user has been removed from the wheel group")
  )

}
