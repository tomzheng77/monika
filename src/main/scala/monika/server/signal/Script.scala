package monika.server.signal

import monika.server.UseScalaz

import scala.language.implicitConversions

trait Script extends UseScalaz {

  val callKey: String = {
    val className = getClass.getSimpleName
    className.flatMap {
      case '$' => ""
      case c if Character.isUpperCase(c) => "-" + c.toLower
      case other => other.toString
    } |> (s => s.dropWhile(_ == '-'))
  }

  def run(args: Vector[String]): SignalResult

  protected implicit def messageToResult(str: String): SignalResult = SignalResult(str)

}
