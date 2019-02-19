package monika.orbit

import java.time.LocalDateTime

import scalaz.{@@, Tag}

object Domain {

  case class Confirm(name: String @@ ConfirmName, time: LocalDateTime, keyName: Option[String @@ KeyName])

  sealed trait KeyName
  def KeyName[A](a: A): A @@ KeyName = Tag(a)

  sealed trait KeyValue
  def KeyValue[A](a: A): A @@ KeyValue = Tag(a)

  sealed trait ConfirmName
  def ConfirmName[A](a: A): A @@ ConfirmName = Tag(a)

  case class State(
    keys: Map[String @@ KeyName, String @@ KeyValue],
    confirms: Map[String @@ ConfirmName, Confirm]
  )

}
