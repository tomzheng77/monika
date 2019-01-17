package monika.server

import java.time.LocalDateTime

import monika.Primitives.FileName
import monika.server.LittleProxy.ProxySettings
import monika.server.signal.Script
import scalaz.@@

object Structs {

  /**
    * A profile is a set of restrictions on which programs, projects and websites
    * a user can have to while it is active
    *
    * @param name name of this profile, must be unique
    * @param programs names of programs allowed when this mode is active
    * @param projects names of projects allowed when this mode is active
    * @param bookmarks bookmarks to display inside the browser for convenience
    * @param proxy restricts which websites can be accessed
    */
  case class Profile(
    name: String,
    programs: Vector[String @@ FileName],
    projects: Vector[String @@ FileName],
    bookmarks: Vector[Bookmark],
    proxy: ProxySettings
  )

  /**
    * Things Monika must remember across multiple sessions
    * @param queue actions to perform in the future
    * @param proxy settings which the proxy was last set to
    */
  case class MonikaState(
    queue: Vector[FutureAction] = Vector.empty,
    proxy: ProxySettings = ProxySettings()
  )

  case class FutureAction(at: LocalDateTime, script: Script, args: Vector[String] = Vector.empty)

  /**
    * a bookmark to display on the browser's toolbar
    * @param name name to display, if not provided should be a shortened url
    * @param url url it should lead to
    */
  case class Bookmark(name: String, url: String)

}
