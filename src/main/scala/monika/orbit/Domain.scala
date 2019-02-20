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

  case class OrbitState(
    seed: Int,
    keys: Map[String @@ KeyName, String @@ KeyValue],
    confirms: Vector[Confirm],
    notes: Vector[String]
  )

  type ST[A] = State[OrbitState, A]
  def ST[A](fn: OrbitState ⇒ (OrbitState, A)) = State(fn)
  def query[A](fn: OrbitState ⇒ A): ST[A] = ST(st ⇒ st → fn(st))
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
    else if (args(0).trim.isEmpty) unit("confirm-name cannot be empty")
    else if (parseDate(args(1).trim).isFailure) unit("confirm-date is invalid")
    else if (parseTime(args(2).trim).isFailure) unit("confirm-time is invalid")
    else {
      val name = ConfirmName(args(0).trim)
      val date = parseDate(args(1).trim).get
      val time = parseTime(args(2).trim).get
      val dateAndTime = LocalDateTime.of(date, time)
      val confirm = Confirm(name, dateAndTime, None)
      appendConfirm(confirm).mapTo(s"confirm $name added at ${dateAndTime.format()}")
    }
  }

  def confirm(args: Vector[String]): ST[String] = {
    if (args.length != 1) unit("confirm <confirm-name>")
    else if (args(0).trim.isEmpty) unit("confirm-name cannot be empty")
    else {
      val name = ConfirmName(args(0).trim)
      existsConfirmWithName(name).flatMap {
        case true ⇒ removeConfirmIf(nameEquals(name)).mapTo(s"$name has been confirmed")
        case false ⇒ unit(s"confirm-name $name does not exist")
      }
    }
  }

  private def nameEquals(name: String @@ ConfirmName)(confirm: Confirm): Boolean = confirm.name == name
  private def existsConfirmWithName(name: String @@ ConfirmName): ST[Boolean] = query(st ⇒ st.confirms.exists(nameEquals(name)))
  private def appendConfirm(confirm: Confirm): ST[Unit] = update(state ⇒ {
    state.copy(confirms = state.confirms.filterNot(nameEquals(confirm.name)) :+ confirm)
  })
  private def removeConfirmIf(fn: Confirm ⇒ Boolean): ST[Unit] = {
    update(st ⇒ st.copy(confirms = st.confirms.filterNot(fn)))
  }

}
