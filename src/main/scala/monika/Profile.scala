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

  case class Program(name: String) extends AnyVal
  case class Project(name: String) extends AnyVal
  case class Bookmark(name: String, url: String)
  case class ProfileSettings(name: String, programs: Vector[Program], projects: Vector[Project], bookmarks: Vector[Bookmark], proxy: ProxySettings)

  /**
    * @param startTime the start time of this profile
    * @param endTime the end time of this profile
    * @param profile which profile should be used throughout the duration
    */
  case class ProfileInQueue(startTime: LocalDateTime, endTime: LocalDateTime, profile: ProfileSettings)

  def profileFromJson(json: JValue): Option[ProfileSettings] = {
    implicit val formats: Formats = DefaultFormats
    json.extractOpt[ProfileSettings]
  }

}
