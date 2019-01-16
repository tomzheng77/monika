package monika

import scalaz.{@@, Tag}

import scala.util.Try

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

}
