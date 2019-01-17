package monika.server.signal

import java.io.PrintWriter

import monika.server.{Hibernate, UseDateTime, UseScalaz}

object Status extends Script with UseScalaz with UseDateTime {
  override def run(args: Vector[String], out: PrintWriter): Unit = {
    Hibernate.transaction(state => {
      val list = state.queue.map(item => {
        s"${item.at.format(DefaultFormatter)}}: ${item.script.callKey}"
      }).mkString("\n")
      (state, list)
    }) |> out.println
  }
}
