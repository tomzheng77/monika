package monika.server.script.wheel

import monika.server.UseDateTime
import monika.server.script.Script

object AddWheel extends Script with UseDateTime {

  override def run(args: Vector[String]): SC[Unit] = steps(
    addToWheelGroup(),
    printLine("the user has been added to the wheel group")
  )

}
