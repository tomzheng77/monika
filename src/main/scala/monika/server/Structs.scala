package monika.server

import java.time.LocalDateTime

import monika.server.proxy.{Filter, TransparentFilter}
import monika.server.script.Script

object Structs {

  /**
    * Things Monika must remember across multiple sessions
    * @param root whether the user can run privileged scripts
    * @param queue actions to perform in the future
    * @param filter settings which the proxy was last set to
    * @param previous the last action that was performed
    */
  case class MonikaState(
    root: Boolean = true,
    queue: Vector[Action] = Vector.empty,
    filter: Filter = TransparentFilter,
    previous: Option[Action] = None
  )

  case class Action(at: LocalDateTime, script: Script, args: Vector[String] = Vector.empty)

}
