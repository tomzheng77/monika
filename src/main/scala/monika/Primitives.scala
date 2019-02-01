package monika

import scalaz.{@@, Tag}

import scala.util.Try

object Primitives {

  sealed trait CanonicalPath
  def CanonicalPath[A](a: A): A @@ CanonicalPath = Tag[A, CanonicalPath](a)

  sealed trait Filename
  def Filename[A](a: A): A @@ Filename = Tag[A, Filename](a)

  implicit class TryExt[T](val t: Try[T]) extends AnyVal {
    def orElseX[U >: T](fn: Throwable => U): U = t.fold(fn, i => i)
  }

}
