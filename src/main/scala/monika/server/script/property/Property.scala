package monika.server.script.property

trait Property {

  import scalaz.syntax.id._
  val name: String = {
    val className = getClass.getSimpleName
    className.flatMap {
      case '$' => ""
      case c if Character.isUpperCase(c) => "-" + c.toLower
      case other => other.toString
    } |> (s => s.dropWhile(_ == '-'))
  }

}
