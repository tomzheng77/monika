package monika.server.signal

import monika.Primitives.TimeFormat
import monika.server.Persistence

object ShowQueue extends Signal {
  override def run(args: Vector[String]): String = {
    Persistence.transaction(state => {
      val list = state.queue.map(item => {
        s"${item._1.format(TimeFormat)}}: ${item._2}"
      }).mkString("\n")
      (state, list)
    })
  }
}
