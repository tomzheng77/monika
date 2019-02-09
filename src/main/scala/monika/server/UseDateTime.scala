package monika.server

import java.time.LocalDateTime

import scalaz.syntax.ToIdOps

import scala.util.Try

trait UseDateTime extends ToIdOps {

  protected val DateTimeFormatter = java.time.format.DateTimeFormatter
  protected type DateTimeFormatter = java.time.format.DateTimeFormatter

  protected val LocalDate = java.time.LocalDate
  protected type LocalDate = java.time.LocalDate

  protected val LocalTime = java.time.LocalTime
  protected type LocalTime = java.time.LocalTime

  protected implicit val dateTimeOrder: Ordering[LocalDateTime] = (x: LocalDateTime, y: LocalDateTime) => x.compareTo(y)

  protected val DefaultFormatter: DateTimeFormatter = {
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
  }

  private val DateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  private val TimeFormatA = DateTimeFormatter.ofPattern("H:mm:ss")
  private val TimeFormatB = DateTimeFormatter.ofPattern("H:mm")

  protected def parseDateTime(str: String): Try[LocalDateTime] = {
    Try(LocalDateTime.parse(str, DefaultFormatter))
  }

  protected def parseDate(str: String): Try[LocalDate] = {
    Try(LocalDate.parse(str, DateFormat))
  }

  protected def parseTime(str: String): Try[LocalTime] = {
    Try(LocalTime.parse(str, TimeFormatA)).orElse(Try(LocalTime.parse(str, TimeFormatB)))
  }

}
