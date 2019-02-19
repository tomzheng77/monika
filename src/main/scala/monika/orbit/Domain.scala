package monika.orbit

import java.time.LocalDateTime

import scalaz.{@@, State, Tag}

object Domain {

  case class Confirm(name: String @@ ConfirmName, time: LocalDateTime, keyName: Option[String @@ KeyName])

  sealed trait KeyName
  def KeyName[A](a: A): A @@ KeyName = Tag(a)

  sealed trait KeyValue
  def KeyValue[A](a: A): A @@ KeyValue = Tag(a)

  sealed trait ConfirmName
  def ConfirmName[A](a: A): A @@ ConfirmName = Tag(a)

  case class OrbitState(
    seed: Int,
    keys: Map[String @@ KeyName, String @@ KeyValue],
    confirms: Map[String @@ ConfirmName, Confirm]
  )

  type ST[A] = State[OrbitState, A]
  def ST[A](fn: OrbitState ⇒ (OrbitState, A)) = State(fn)



}
