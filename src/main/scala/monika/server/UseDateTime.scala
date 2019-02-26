package monika.server

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{LocalDate, LocalDateTime, LocalTime}

import scalaz.syntax.ToIdOps

import scala.util.{Failure, Success, Try}

trait UseDateTime extends ToIdOps {

  protected type DateTimeFormatter = java.time.format.DateTimeFormatter
  protected type LocalDate = java.time.LocalDate
  protected type LocalTime = java.time.LocalTime
  protected type LocalDateTime = java.time.LocalDateTime

  protected implicit val dateTimeOrder: Ordering[LocalDateTime] = (x: LocalDateTime, y: LocalDateTime) => x.compareTo(y)

  private val DefaultFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
  private val DateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  private val TimeFormatterA = DateTimeFormatter.ofPattern("H:mm:ss")
  private val TimeFormatterB = DateTimeFormatter.ofPattern("H:mm")

  implicit class LocalDateTimeExt(dt: LocalDateTime) {
    def format(): String = dt.format(DefaultFormatter)
    def untilHMS(til: LocalDateTime): String = {
      val secondsLeft = dt.until(til, ChronoUnit.SECONDS)
      def format00(n: Long): String = ("0" + n.toString).takeRight(2)
      val hours = format00(secondsLeft / 60 / 60)
      val minutes = format00(secondsLeft / 60 % 60)
      val seconds = format00(secondsLeft % 60)
      s"$hours:$minutes:$seconds"
    }
  }

  implicit class TryExtDateTime[T <: LocalDateTime](t: Try[T]) {
    def getOrThrow(): T = t match {
      case Success(a) ⇒ a
      case Failure(ex) ⇒ throw ex
    }
  }

  protected def parseDateTime(str: String): Try[LocalDateTime] = {
    Try(LocalDateTime.parse(str, DefaultFormatter))
  }

  protected def parseDate(str: String): Try[LocalDate] = {
    Try(LocalDate.parse(str, DateFormatter))
  }

  protected def parseTime(str: String): Try[LocalTime] = {
    Try(LocalTime.parse(str, TimeFormatterA)).orElse(Try(LocalTime.parse(str, TimeFormatterB)))
  }

}
