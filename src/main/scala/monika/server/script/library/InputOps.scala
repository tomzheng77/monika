package monika.server.script.library

trait InputOps {

  def toSet(input: String): Set[String] = {
    input.split(',').toSet.map(_.trim).filter(_.nonEmpty)
  }

}
