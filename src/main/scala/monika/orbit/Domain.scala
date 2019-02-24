package monika.orbit

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

import monika.server.UseDateTime
import scalaz.Tag.unwrap
import scalaz.{@@, State, Tag}

import scala.language.implicitConversions
import scala.util.Try

object Domain extends UseDateTime {

  sealed trait ConfirmName
  def ConfirmName[A <: String](a: A): A @@ ConfirmName = Tag(a)

  sealed trait KeyName
  def KeyName[A <: String](a: A): A @@ KeyName = Tag(a)

  sealed trait KeyValue
  def KeyValue[A <: String](a: A): A @@ KeyValue = Tag(a)

  sealed trait Minutes
  def Minutes[A <: Int](a: A): A @@ Minutes = Tag(a)

  case class Confirm(
    name: String @@ ConfirmName,
    time: LocalDateTime,
    window: Int @@ Minutes,
    key: Option[String @@ KeyName]
  ) {
    val start: LocalDateTime = time.minusMinutes(unwrap(window))
  }

  case class Key(name: String @@ KeyName, value: String @@ KeyValue)

  case class OrbitState(
    keys: Vector[Key],
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

  def initialState: OrbitState = {
    OrbitState(Vector.empty, Vector.empty, Vector.empty)
  }

  def handle(args: Vector[String])(nowTime: LocalDateTime): ST[String] = {
    args.headOption.getOrElse("").trim match {
      case "" ⇒ listNotesOrConfirms()
      case "add-key" ⇒ addKey(args.drop(1))
      case "remove-key" ⇒ removeKey(args.drop(1))
      case "add-note" ⇒ addNote(args.drop(1))
      case "add-confirm" ⇒ addConfirm(args.drop(1))(nowTime)
      case "confirm" ⇒ confirm(args.drop(1))(nowTime)
      case other ⇒ unit(s"command '$other' is not recognised")
    }
  }

  private def listNotesOrConfirms(): ST[String] = query(st ⇒ {
    val output = new StringBuilder()
    if (st.confirms.nonEmpty) {
      output.append("==========[Confirms]==========").append('\n')
      for (confirm ← st.confirms) {
        output.append(s"- ${confirm.name}: ${confirm.time.format()} (window: ${confirm.window})").append('\n')
      }
    } else {
      output.append("==========[Notes]==========").append('\n')
      for ((note, index) ← st.notes.zipWithIndex) {
        output.append(s"- #${index + 1}: $note").append('\n')
      }
      output.append("==========[Keys]==========").append('\n')
      for (Key(name, value) ← st.keys) {
        output.append(s"- $name: $value").append('\n')
      }
    }
    output.toString()
  })

  private def addNote(args: Vector[String]): ST[String] = {
    if (args.length != 1) unit("add-note <note-text>")
    else if (args(0).trim.isEmpty) unit("note-text cannot be empty")
    else appendNote(args(0)).map(index ⇒ s"the note #${index + 1} has been added")
  }

  private def addKey(args: Vector[String]): ST[String] = {
    if (args.length != 2) unit("add-key <key-name> <key-value>")
    else if (args(0).trim.isEmpty) unit("key-name cannot be empty")
    else if (args(1).trim.isEmpty) unit("key-value cannot be empty")
    else {
      val keyName = KeyName(args(0).trim)
      val keyValue = keyValue(args(1).trim)
      val key = Key(keyName, keyValue)
      findKeyWithName(keyName).flatMap {
        case Some(_) ⇒ unit(s"key '$keyName' already exists")
        case None ⇒ update(state ⇒ state.copy(keys = state.keys :+ key)).mapTo(s"key '$keyName' has been added")
      }
    }
  }

  private def removeKey(args: Vector[String]): ST[String] = {
    if (args.length != 1) unit("remove-key <key-name>")
    else if (args(0).trim.isEmpty) unit("key-name cannot be empty")
    else {
      val keyName = KeyName(args(0).trim)
      findKeyWithName(keyName).flatMap {
        case Some(key) ⇒ removeKeyIf(k ⇒ k.name == keyName).mapTo(s"key '${key.name}' has been removed")
        case None ⇒ unit(s"key '$keyName' does not exist")
      }
    }
  }

  private def addConfirm(args: Vector[String])(nowTime: LocalDateTime): ST[String] = {
    if (args.length != 4) unit("add-confirm <confirm-name> <confirm-date> <confirm-time> <window> [<key-name>]")
    else if (args(0).trim.isEmpty) unit("confirm-name cannot be empty")
    else if (parseDate(args(1).trim).isFailure) unit("confirm-date is invalid")
    else if (parseTime(args(2).trim).isFailure) unit("confirm-time is invalid")
    else if (Try(args(3).toInt).filter(_ > 0).isFailure) unit("window is invalid")
    else {
      val name = ConfirmName(args(0).trim)
      val date = parseDate(args(1).trim).get
      val time = parseTime(args(2).trim).get
      val window = args(3).toInt
      val dateAndTime = LocalDateTime.of(date, time)
      if (dateAndTime.isBefore(nowTime.plusMinutes(1))) unit("confirm must be at least one minute after now")
      else {
        val confirm = Confirm(name, dateAndTime, Minutes(window), None)
        findConfirmWithName(name).flatMap {
          case None ⇒ appendConfirm(confirm).mapTo(s"confirm $name added at ${dateAndTime.format()}")
          case Some(c) ⇒ unit(s"confirm ${c.name} already exists at ${c.time.format()}")
        }
      }
    }
  }

  private def confirm(args: Vector[String])(nowTime: LocalDateTime): ST[String] = {
    if (args.length != 1) unit("confirm <confirm-name> [<key-value>]")
    else if (args(0).trim.isEmpty) unit("confirm-name cannot be empty")
    else {
      val name = ConfirmName(args(0).trim)
      findConfirmWithName(name).flatMap {
        case None ⇒ unit(s"confirm-name $name does not exist")
        case Some(confirm) if nowTime.isBefore(confirm.start) ⇒ {
          val secondsLeft = nowTime.until(confirm.start, ChronoUnit.SECONDS)
          def format00(n: Long): String = ("0" + n.toString).takeRight(2)
          val hours = format00(secondsLeft / 60 / 60)
          val minutes = format00(secondsLeft / 60 % 60)
          val seconds = format00(secondsLeft % 60)
          unit(s"please wait $hours:$minutes:$seconds")
        }
        case Some(_) ⇒ removeConfirmIf(nameEquals(name)).mapTo(s"$name has been confirmed")
      }
    }
  }

  private def nameEquals(name: String @@ ConfirmName)(confirm: Confirm): Boolean = confirm.name == name
  private def findConfirmWithName(name: String @@ ConfirmName): ST[Option[Confirm]] = query(st ⇒ st.confirms.find(nameEquals(name)))
  private def appendConfirm(confirm: Confirm): ST[Unit] = update(state ⇒ {
    state.copy(confirms = state.confirms.filterNot(nameEquals(confirm.name)) :+ confirm)
  })
  private def removeConfirmIf(fn: Confirm ⇒ Boolean): ST[Unit] = {
    update(st ⇒ st.copy(confirms = st.confirms.filterNot(fn)))
  }
  private def findKeyWithName(name: String @@ KeyName): ST[Option[Key]] = query(st ⇒ st.keys.find(_.name == name))
  private def removeKeyIf(fn: Key ⇒ Boolean): ST[Unit] = {
    update(st ⇒ st.copy(keys = st.keys.filterNot(fn)))
  }
  private def appendNote(note: String): ST[Int] = {
    ST(st ⇒ st.copy(notes = st.notes :+ note) → st.notes.length)
  }

}
