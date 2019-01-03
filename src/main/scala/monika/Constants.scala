package monika

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

  object paths {
    val Root: String = "/home/shared"
    val StateDB: String = Root + "/state.db"
    val Profiles: String = Root + "/profiles"
    val Projects: String = Root + "/projects"
  }

}
