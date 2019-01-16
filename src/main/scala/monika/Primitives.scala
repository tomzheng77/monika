package monika

import java.time.format.DateTimeFormatter

import org.slf4j.Logger
import scalaz.{@@, Tag}

import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object Primitives {

  /**
    * - the canonical path of a file or folder
    * - by definition, a canonical path is both absolute and unique
    */
  sealed trait FilePath
  def FilePath[A](a: A): A @@ FilePath = Tag[A, FilePath](a)

  /**
    * - the name of a file or folder relative to it's parent
    */
  sealed trait FileName
  def FileName[A](a: A): A @@ FileName = Tag[A, FileName](a)

  implicit class TryExt[T](val t: Try[T]) extends AnyVal {
    def orElseX[U >: T](fn: Throwable => U): U = t.fold(fn, i => i)
  }

  implicit class LoggerExt(val l: Logger) extends AnyVal {
    def logIfFail[T](message: String)(r: => T): Try[T] =
      try Success(r) catch {
        case NonFatal(e) => l.error(message, r); Failure(e)
      }
  }

  val TimeFormat: DateTimeFormatter = {
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
  }

}
