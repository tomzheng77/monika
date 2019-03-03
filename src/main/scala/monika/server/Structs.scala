package monika.server

import java.time.{LocalDateTime, ZoneOffset}

import monika.server.proxy.{Filter, TransparentFilter}
import monika.server.script.Script
import monika.server.script.internal.Unlock

object Structs {

  private val Epoch = LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC)

  /**
    * Things Monika must remember across multiple sessions
    * @param root whether the user can run privileged scripts
    * @param queue actions to perform in the future
    * @param filter settings which the proxy was last set to
    */
  case class MonikaState(
    root: Boolean = true,
    queue: Vector[Action] = Vector.empty,
    filter: Filter = TransparentFilter,
    lastAction: Action = Action(Epoch, Unlock)
  )

  case class Action(at: LocalDateTime, script: Script, args: Vector[String] = Vector.empty)

}
