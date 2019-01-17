package monika.server.signal

import java.io.PrintWriter

import monika.server.Structs.FutureAction
import monika.server.{Hibernate, UseDateTime, UseScalaz}

object Status extends Script with UseScalaz with UseDateTime {

  override def run(args: Vector[String], out: PrintWriter): Unit = {
    val state = Hibernate.readStateOrDefault()
    for (FutureAction(at, script, args) <- state.queue) {
      out.println(s"${at.format(DefaultFormatter)}}: ${script.name} ${args.mkString(" ")}")
    }
  }

}
