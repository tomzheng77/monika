package monika

object Profile {

  case class Proxy(allowHtmlPrefix: Vector[String], rejectHtmlKeywords: Vector[String])
  case class Bookmark(name: String, url: String)
  case class Configuration(name: String, programs: Vector[String], bookmarks: String, projects: Vector[String], proxy: Proxy)
  case class QueueItem(configuration: Configuration, time: Int)

}
