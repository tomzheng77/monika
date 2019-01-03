package monika

object Constants {

  val Users = Vector(
    "tomzheng",
    "profile",
    "unlocker"
  )

  val ProfileUser = "profile"
  val ProxyPort = 9000
  val InterpreterPort = 9001
  val MaxQueueSize = 3

  object Paths {
    val Root: String = "/home/shared"
    val StateDB: String = Root + "/state.db"
    val Profiles: String = Root + "/profiles"
  }

}
