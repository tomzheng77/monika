package monika.server

import scalaz.syntax.ToIdOps

trait UseTry extends ToIdOps {

  type Success[+T] = scala.util.Success[T]
  type Failure[+T] = scala.util.Failure[T]
  val Success = scala.util.Success
  val Failure = scala.util.Failure

}
