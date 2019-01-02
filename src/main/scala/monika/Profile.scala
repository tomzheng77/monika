package monika

object Profile {

  case class Bookmark(name: String, url: String)
  case class Configuration(name: String, allowHtmlPrefix: String, programs: Vector[String], bookmarks: String, projects: Vector[String])

}
