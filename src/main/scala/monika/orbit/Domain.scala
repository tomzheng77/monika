package monika.orbit

import java.security.SecureRandom
import java.time.LocalDateTime

import monika.server.UseDateTime
import scalaz.effect.IO
import scalaz.{@@, State, Tag}

import scala.language.implicitConversions

object Domain extends UseDateTime {

  sealed trait KeyName
  def KeyName[A <: String](a: A): A @@ KeyName = Tag(a)

  sealed trait KeyValue
  def KeyValue[A <: String](a: A): A @@ KeyValue = Tag(a)

  sealed trait ConfirmName
  def ConfirmName[A <: String](a: A): A @@ ConfirmName = Tag(a)

  case class Confirm(name: String @@ ConfirmName, time: LocalDateTime, keyName: Option[String @@ KeyName])
  def hasName(name: String @@ ConfirmName)(confirm: Confirm): Boolean = confirm.name == name
  def appendConfirm(confirm: Confirm)(state: OrbitState): OrbitState = {
    state.copy(confirms = state.confirms.filterNot(hasName(confirm.name)) :+ confirm)
  }

  case class OrbitState(
    seed: Int,
    keys: Map[String @@ KeyName, String @@ KeyValue],
    confirms: Vector[Confirm],
    notes: Vector[String]
  )

  type ST[A] = State[OrbitState, A]
  def ST[A](fn: OrbitState ⇒ (OrbitState, A)) = State(fn)
  def update(fn: OrbitState ⇒ OrbitState): ST[Unit] = ST(st ⇒ fn(st) → ())
  def unit[A](a: A): ST[A] = State.state(a)

  implicit class AnyST[A](st: ST[A]) {
    def mapTo[B](b: B): ST[B] = st.map(_ ⇒ b)
  }

  def initialState: IO[OrbitState] = IO {
    val random = new SecureRandom()
    OrbitState(random.nextInt(), Map.empty, Vector.empty, Vector.empty)
  }

  def handle(args: Vector[String]): ST[String] = {
    args.headOption.getOrElse("").trim match {
      case "" ⇒ unit("please provide a command")
      case "add-key" ⇒ unit("this command has not been implemented")
      case "add-confirm" ⇒ addConfirm(args.drop(1))
      case "confirm" ⇒ unit("please provide a command")
      case other ⇒ unit(s"command '$other' is not recognised")
    }
  }

  def addConfirm(args: Vector[String]): ST[String] = {
    if (args.length != 3) unit("add-key <confirm-name> <confirm-date> <confirm-time>")
    if (args(0).trim.isEmpty) unit("confirm-name cannot be empty")
    if (parseDate(args(1).trim).isFailure) unit("confirm-date is invalid")
    if (parseTime(args(2).trim).isFailure) unit("confirm-time is invalid")
    else {
      val name = ConfirmName(args(0).trim)
      val date = parseDate(args(1).trim).get
      val time = parseTime(args(2).trim).get
      val dateAndTime = LocalDateTime.of(date, time)
      val confirm = Confirm(name, dateAndTime, None)
      update(appendConfirm(confirm)).mapTo(s"confirm $name added at ${dateAndTime.format()}")
    }
  }

  def confirm(name: String): ST[Unit] = ST(state ⇒ {
    if (state.confirms.exists(_.name == ConfirmName(name))) null
    null
  })

}
