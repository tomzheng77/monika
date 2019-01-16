package monika.server.signal

/**
  * - a command can be requested by the user with a set of arguments
  */
trait Signal {

  def run(args: Vector[String]): String

}
