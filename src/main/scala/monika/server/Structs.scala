package monika.server

import java.time.LocalDateTime

import monika.Primitives.FileName
import monika.server.proxy.{Filter, TransparentFilter}
import monika.server.script.Script
import scalaz.@@

object Structs {

  /**
    * Things Monika must remember across multiple sessions
    * @param root whether the user can run privileged scripts
    * @param queue actions to perform in the future
    * @param filter settings which the proxy was last set to
    */
  case class MonikaState(
    root: Boolean = true,
    queue: Vector[FutureAction] = Vector.empty,
    filter: Filter = TransparentFilter,
    solves: Vector[LocalDateTime] = Vector.empty
  )

  case class FutureAction(at: LocalDateTime, script: Script, args: Vector[String] = Vector.empty)

}
