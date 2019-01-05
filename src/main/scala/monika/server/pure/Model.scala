package monika.server.pure

import java.time.{LocalDate, LocalDateTime, ZoneOffset}

import monika.Primitives._
import monika.server.proxy.ProxyServer.ProxySettings
import org.json4s.JsonAST.JValue
import org.json4s.{DefaultFormats, Formats}
import scalaz.@@

object Model {

  /**
    * a bookmark to display on the browser's toolbar
    * @param name name to display, if not provided should be a shortened url
    * @param url url it should lead to
    */
  case class Bookmark(name: String, url: String)

  /**
    * defines which programs, projects and websites the profile user can use
    * when this mode is active
    *
    * @param name name of this mode
    * @param programs names of programs allowed when this mode is active
    * @param projects names of projects allowed when this mode is active
    * @param bookmarks bookmarks to display inside the browser for convenience
    * @param proxy restricts which websites can be accessed
    */
  case class Profile(name: String, programs: Vector[String @@ FileName], projects: Vector[String @@ FileName],
                     bookmarks: Vector[Bookmark], proxy: ProxySettings)

  /**
    * constructs a profile from a .json definition file
    * this is not a deserialization process, it is fault tolerant and provides
    * default values for all fields
    */
  def constructProfile(definition: JValue, defaultName: String): Profile = {
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

  /**
    * @param start the start time of this profile
    * @param end the end time of this profile
    * @param profile which profile should be used throughout the duration
    */
  case class ProfileRequest(start: LocalDateTime, end: LocalDateTime, profile: Profile)

  /**
    * the full runtime state of the Monika program
    * since it has very limited amounts of state (< 10KB), it is completely feasible
    * to represent it with an immutable data structure
    *
    * the profile modes are fully stored within the state, hence prevents them
    * from being modified even if the .json files are changed
    *
    * invariant: the queue is sorted by start time
    * invariant: no two queue items overlap in time
    * invariant: profiles are mapped by their name
    * invariant: the passwords contain only characters and numbers
    */
  case class MonikaState(
    queue: Vector[ProfileRequest],
    active: Option[ProfileRequest],
    knownProfiles: Map[String, Profile],
    passwords: Map[LocalDate, String]
  ) {
    assert(queue.sortBy(i => i.start.toEpochSecond(ZoneOffset.UTC)) == queue)
    private val intervals = queue.map(i => {
      (i.start.toEpochSecond(ZoneOffset.UTC), i.end.toEpochSecond(ZoneOffset.UTC))
    })
    assert(intervals.forall(pair => pair._2 > pair._1))
    assert(intervals.indices.dropRight(1).forall(i => intervals(i)._2 <= intervals(i + 1)._1))
    assert(knownProfiles.forall(pair => pair._1 == pair._2.name))
    assert(passwords.values.forall(pwd => pwd.forall(Character.isLetterOrDigit)))
  }

  val InitialState: MonikaState = {
    MonikaState(Vector(), None, Map.empty, Map.empty)
  }

}
