package monika.server.command

trait Command {

  def run(args: Vector[String]): Unit

}
