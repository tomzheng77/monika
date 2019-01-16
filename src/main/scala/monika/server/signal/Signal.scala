package monika.server.signal

trait Signal {

  def run(args: Vector[String]): String

}
