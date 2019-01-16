package monika.server.signal

import monika.server.{Persistence, UseDateTime, UseScalaz}

object ShowQueue extends Signal with UseScalaz with UseDateTime {
  override def run(args: Vector[String]): SignalResult = {
    Persistence.transaction(state => {
      val list = state.queue.map(item => {
        s"${item.at.format(DefaultFormatter)}}: ${item.action}"
      }).mkString("\n")
      (state, list)
    }) |> (s => SignalResult(message = s))
  }
}
