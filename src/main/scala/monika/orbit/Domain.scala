package monika.orbit

import java.security.SecureRandom
import java.time.LocalDateTime

import monika.server.UseDateTime
import scalaz.effect.IO
import scalaz.{@@, State, Tag}

import scala.language.implicitConversions

object Domain extends UseDateTime {

  sealed trait KeyName
  def KeyName[A](a: A): A @@ KeyName = Tag(a)

  sealed trait KeyValue
  def KeyValue[A](a: A): A @@ KeyValue = Tag(a)

  sealed trait ConfirmName
  def ConfirmName[A](a: A): A @@ ConfirmName = Tag(a)

  case class Confirm(name: String @@ ConfirmName, time: LocalDateTime, keyName: Option[String @@ KeyName])

  case class OrbitState(
    seed: Int,
    keys: Map[String @@ KeyName, String @@ KeyValue],
    confirms: Vector[Confirm],
    notes: Vector[String]
  )

  type ST[A] = State[OrbitState, A]
  def ST[A](fn: OrbitState ⇒ (OrbitState, A)) = State(fn)
  def update(fn: OrbitState ⇒ OrbitState): ST[Unit] = ST(st ⇒ fn(st) → ())
  def STA[A](a: A): ST[A] = State.state(a)

  implicit class AnyST[A](st: ST[A]) {
    def mapTo[B](b: B): ST[B] = st.map(_ ⇒ b)
  }

  def initialState: IO[OrbitState] = IO {
    val random = new SecureRandom()
    OrbitState(random.nextInt(), Map.empty, Vector.empty, Vector.empty)
  }

  def handle(args: Vector[String]): ST[String] = {
    args.headOption.getOrElse("").trim match {
      case "" ⇒ STA("please provide a command")
      case "add-key" ⇒ STA("this command has not been implemented")
      case "add-confirm" ⇒ addConfirm(args.drop(1))
      case "confirm" ⇒ STA("please provide a command")
      case other ⇒ STA(s"command '$other' is not recognised")
    }
  }

  def addConfirm(args: Vector[String]): ST[String] = {
    if (args.length != 3) STA("add-key <confirm-name> <confirm-date> <confirm-time>")
    if (args(0).trim.isEmpty) STA("confirm-name cannot be empty")
    if (parseDate(args(1).trim).isFailure) STA("confirm-date is invalid")
    if (parseTime(args(2).trim).isFailure) STA("confirm-time is invalid")
    else {
      val name = args(0).trim
      val date = parseDate(args(1).trim).get
      val time = parseTime(args(2).trim).get
      val dateAndTime = LocalDateTime.of(date, time)
      val confirm = Confirm(Tag(name), dateAndTime, None)
      update(st ⇒ st.copy(confirms = st.confirms :+ confirm))
        .mapTo(s"confirm $name added at ${dateAndTime.format()}")
    }
  }

  def confirm(name: String): ST[Unit] = ST(state ⇒ {
    if (state.confirms.contains(ConfirmName(name))) null
    null
  })

}
