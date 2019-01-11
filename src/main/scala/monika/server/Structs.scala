package monika.server

import java.time.LocalDateTime

import monika.Primitives.FileName
import monika.server.LittleProxy.ProxySettings
import org.json4s.JsonAST.JValue
import org.json4s.{DefaultFormats, Formats}
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
    queue: Vector[(LocalDateTime, Action)] = Vector.empty,
    proxy: ProxySettings = ProxySettings()
  )

  /**
    * an effect changes the environment of the user
    * it can be one of the following:
    * - SetProfile: sets the environment of the user to the specified profile
    * - Unlock: removes any profile
    */
  sealed trait Action
  case class SetProfile(profile: Profile) extends Action
  case object Unlock extends Action

  /**
    * a bookmark to display on the browser's toolbar
    * @param name name to display, if not provided should be a shortened url
    * @param url url it should lead to
    */
  case class Bookmark(name: String, url: String)

  /**
    * constructs a profile from a .json definition file
    * this is not a deserialization process, it is fault tolerant and provides
    * default values for all fields
    */
  def readProfileFromJSON(definition: JValue, defaultName: String): Profile = {
    implicit val formats: Formats = DefaultFormats
    Profile(
      (definition \ "name").extractOpt[String].getOrElse(defaultName),
      (definition \ "programs").extractOpt[Vector[String]].getOrElse(Vector.empty).map(FileName),
      (definition \ "projects").extractOpt[Vector[String]].getOrElse(Vector.empty).map(FileName),
      (definition \ "bookmarks").extractOpt[Vector[JValue]].getOrElse(Vector.empty).map(v => {
        val url = (v \ "url").extractOpt[String].getOrElse("http://www.google.com")
        val re = "[A-Za-z-]+(\\.[A-Za-z-]+)*\\.[A-Za-z-]+".r
        val name = (v \ "name").extractOpt[String].orElse(re.findFirstIn(url)).getOrElse("Unknown")
        Bookmark(name, url)
      }),
      ProxySettings(
        (definition \ "proxy" \ "transparent").extractOpt[Boolean].getOrElse(false),
        (definition \ "proxy" \ "allowHtmlPrefix").extractOpt[Vector[String]].getOrElse(Vector.empty),
        (definition \ "proxy" \ "rejectHtmlKeywords").extractOpt[Vector[String]].getOrElse(Vector.empty)
      )
    )
  }

}
