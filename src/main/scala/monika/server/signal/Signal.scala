package monika.server.signal

import scala.language.implicitConversions

trait Signal {

  def run(args: Vector[String]): SignalResult

  protected implicit def messageToResult(str: String): SignalResult = SignalResult(str)

}
