package monika.server

import scalaz.syntax.ToIdOps

trait UseScalaz extends ToIdOps {

  type Reader[E, A] = scalaz.Reader[E, A]
  def Reader[E, A](f: E => A): Reader[E, A] = scalaz.Reader[E, A](f)

}
