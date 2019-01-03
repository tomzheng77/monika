package monika

import Profile._
import scalaz.@@

object Constants {

  val Users = Vector(
    "tomzheng",
    "profile",
    "unlocker"
  )

  val MainUser = "tomzheng"
  val ProfileUser = "profile"
  val ProxyPort = 9000
  val InterpreterPort = 9001
  val MaxQueueSize = 3

  val ManagedPrograms: Vector[String @@ FileName] = Vector(
    "studio",
    "subl",
    "idea",
    "firefox",
    "google-chrome",
    "steam",
    "virtualbox",
    "jetbrains-toolbox",
    "wine",
    "libreoffice",
    "ssh",
    "assistant.jar"
  ).map(FileName)

  object paths {
    val MonikaHome: String = "/home/tomzheng/monika"
    val StateDB: String = MonikaHome + "/state.db"
    val ProfileRoot: String = MonikaHome + "/profiles"
    val ProjectRoot: String = MonikaHome + "/projects"
    val ChromeBookmark: String = "/home/profile/.config/google-chrome/Default/Bookmarks"
  }

}
