package monika

import java.time.LocalDateTime

import org.json4s.JsonAST.JValue
import org.json4s.{DefaultFormats, Formats}

object Profile {

  /**
    * configures the behaviour of the HTTP/HTTPS proxy, which all requests of the profile user must pass through
    * @param transparent whether the proxy should not perform filtering at all
    *                    if this is set to true, allow/reject properties will be ignored
    *                    in addition, no certificate will be required
    * @param allowHtmlPrefix which text/html responses should be allowed through if the url starts with a prefix
    * @param rejectHtmlKeywords which text/html responses should be rejected if they contain one of the keywords
    */
  case class ProxySettings(transparent: Boolean, allowHtmlPrefix: Vector[String], rejectHtmlKeywords: Vector[String])

  /**
    * represents a linux/unix program which the profile user can run
    * when the corresponding mode is active
    */
  case class Program(name: String) extends AnyVal

  /**
    * represents a folder inside project home which the profile user can
    * edit (including all sub-contents) when the corresponding mode is active
    */
  case class Project(name: String) extends AnyVal

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
    * @param programs programs allowed when this mode is active
    * @param projects projects allowed when this mode is active
    * @param bookmarks bookmarks to display inside the browser for convenience
    * @param proxy restricts which websites can be accessed
    */
  case class ProfileMode(name: String, programs: Vector[Program], projects: Vector[Project], bookmarks: Vector[Bookmark], proxy: ProxySettings)

  /**
    * @param startTime the start time of this profile
    * @param endTime the end time of this profile
    * @param profile which profile should be used throughout the duration
    */
  case class ProfileInQueue(startTime: LocalDateTime, endTime: LocalDateTime, profile: ProfileMode)

  def profileFromJson(json: JValue): Option[ProfileMode] = {
    implicit val formats: Formats = DefaultFormats
    json.extractOpt[ProfileMode]
  }

}
