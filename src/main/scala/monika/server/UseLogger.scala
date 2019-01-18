package monika.server

import org.slf4j.{Logger, LoggerFactory}

import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

trait UseLogger {

  protected val LOGGER: Logger = LoggerFactory.getLogger(getClass)

  implicit class LoggerExt(val l: Logger) {
    def logIfFail[T](message: String)(r: => T): Try[T] =
      try Success(r) catch {
        case NonFatal(e) => l.error(message, r); Failure(e)
      }
  }

}
