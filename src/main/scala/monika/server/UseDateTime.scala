package monika.server

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import scalaz.syntax.ToIdOps

trait UseDateTime extends ToIdOps {

  protected implicit val dateTimeOrder: Ordering[LocalDateTime] = (x: LocalDateTime, y: LocalDateTime) => x.compareTo(y)

  protected val DefaultDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  protected val DefaultTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

  protected val DefaultFormatter: DateTimeFormatter = {
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
  }

}
