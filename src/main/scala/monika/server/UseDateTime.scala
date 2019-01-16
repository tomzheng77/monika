package monika.server

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import scalaz.syntax.ToIdOps

trait UseDateTime extends ToIdOps {

  protected implicit val dateTimeOrder: Ordering[LocalDateTime] = (x: LocalDateTime, y: LocalDateTime) => x.compareTo(y)

  protected val DefaultFormatter: DateTimeFormatter = {
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
  }

}
