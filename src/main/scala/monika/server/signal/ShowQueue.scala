package monika.server.signal

import monika.Primitives.TimeFormat
import monika.server.{Persistence, UseScalaz}

object ShowQueue extends Signal with UseScalaz {
  override def run(args: Vector[String]): SignalResult = {
    Persistence.transaction(state => {
      val list = state.queue.map(item => {
        s"${item.at.format(TimeFormat)}}: ${item.action}"
      }).mkString("\n")
      (state, list)
    }) |> (s => SignalResult(message = s))
  }
}
