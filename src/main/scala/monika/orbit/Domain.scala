package monika.orbit

import java.time.LocalDateTime

import monika.server.{UseDateTime, UseJSON}
import scalaz.Tag.unwrap
import scalaz.{@@, State, Tag}
import org.json4s.JValue
import org.json4s.jackson.JsonMethods._

import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}

object Domain extends UseDateTime with UseJSON {

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
    key: Option[String @@ KeyValue]
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
      case "list-confirms" ⇒ listConfirms()
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

  private def listConfirms(): ST[String] = {
    query(st ⇒ {
      val json: JValue = st.confirms.map(c ⇒ {
        ("name" → unwrap(c.name)) ~
        ("time" → c.time.format()) ~
        ("window" → unwrap(c.window)) ~
        ("hasKey" → c.key.nonEmpty)
      })
      pretty(render(json))
    })
  }

  private def addNote(args: Vector[String]): ST[String] =   for {
    _        ← check(args.length == 1)("add-note <note-text>")
    noteText ← require(args(0).trim)(notEmpty)("note-text cannot be empty")
  } yield appendNote(noteText).map(i ⇒ s"the note #${i + 1} has been added")

  private def addKey(args: Vector[String]): ST[String] = for {
    _        ← check(args.length == 2)("add-key <key-name> <key-value>")
    keyName  ← require(args(0).trim)(notEmpty)("key-name cannot be empty").map(KeyName)
    keyValue ← require(args(1).trim)(notEmpty)("key-value cannot be empty").map(KeyValue)
  } yield {
    val key = Key(keyName, keyValue)
    findKeyWithName(keyName).flatMap {
      case Some(_) ⇒ unit(s"key '$keyName' already exists")
      case None ⇒ update(state ⇒ state.copy(keys = state.keys :+ key)).mapTo(s"key '$keyName' has been added")
    }
  }

  private def removeKey(args: Vector[String]): ST[String] = for {
    _       ← check(args.length == 1)("remove-key <key-name>")
    keyName ← require(args(0).trim)(notEmpty)("key-name cannot be empty").map(KeyName)
  } yield {
    findKeyWithName(keyName).flatMap {
      case Some(key) ⇒ removeKeyIf(k ⇒ k.name == keyName).mapTo(s"key '${key.name}' has been removed")
      case None ⇒ unit(s"key '$keyName' does not exist")
    }
  }

  private def addConfirm(args: Vector[String])(nowTime: LocalDateTime): ST[String] = for {
    _      ← check(args.lengthOneOf(4, 5))("add-confirm <confirm-name> <confirm-date> <confirm-time> <window> [<key-name>]")
    name   ← require(args(0).trim)(notEmpty)("confirm-name cannot be empty").map(ConfirmName)
    date   ← require(args(1).trim |> parseDate |> (_.get))(pass)("confirm-date is invalid")
    time   ← require(args(2).trim |> parseTime |> (_.get))(pass)("confirm-time is invalid")
    window ← require(args(3).toInt)(pass)("window is invalid")
    dateAndTime = LocalDateTime.of(date, time)
    _      ← check(dateAndTime.isAfter(nowTime.plusMinutes(1)))("confirm must be at least one minute after now")
    keyNameOption = optionalValue(args(4).trim)(notEmpty).map(KeyName)
  } yield {
    keyNameOption match {
      case None ⇒ addConfirmInternal(Confirm(name, dateAndTime, Minutes(window), None))
      case Some(keyName) ⇒ findKeyWithName(keyName).flatMap {
        case Some(key) ⇒ addConfirmInternal(Confirm(name, dateAndTime, Minutes(window), Some(key.value)))
        case None ⇒ unit(s"key '$keyName' does not exist")
      }
    }
  }

  private def addConfirmInternal(confirm: Confirm): ST[String] = {
    findConfirmWithName(confirm.name).flatMap {
      case None ⇒ appendConfirm(confirm).mapTo(s"confirm ${confirm.name} added at ${confirm.time.format()}")
      case Some(c) ⇒ unit(s"confirm ${c.name} already exists at ${c.time.format()}")
    }
  }

  private def confirm(args: Vector[String])(nowTime: LocalDateTime): ST[String] = for {
    _        ← check(args.lengthOneOf(1, 2))("confirm <confirm-name> [<key-value>]")
    name     ← require(args(0).trim)(_.nonEmpty)("confirm-name cannot be empty").map(ConfirmName)
    keyValue = optionalValue(args(1).trim)(notEmpty).map(KeyValue)
  } yield {
    findConfirmWithName(name).flatMap {
      case None ⇒ unit(s"confirm-name $name does not exist")
      case Some(c) if nowTime.isBefore(c.start) ⇒ unit(s"please wait ${nowTime.untilHMS(c.start)}")
      case Some(c) if c.key.nonEmpty && keyValue.isEmpty ⇒ unit(s"$name requires a key")
      case Some(c) if c.key.nonEmpty && c.key != keyValue ⇒ unit(s"the key does not match")
      case Some(_) ⇒ removeConfirmIf(nameEquals(name)).mapTo(s"$name has been confirmed")
    }
  }

  private def nameEquals(name: String @@ ConfirmName)(confirm: Confirm): Boolean = {
    confirm.name == name
  }

  private def findConfirmWithName(name: String @@ ConfirmName): ST[Option[Confirm]] = {
    query(st ⇒ st.confirms.find(nameEquals(name)))
  }

  private def appendConfirm(confirm: Confirm): ST[Unit] = update(state ⇒ {
    state.copy(confirms = state.confirms.filterNot(nameEquals(confirm.name)) :+ confirm)
  })

  private def removeConfirmIf(fn: Confirm ⇒ Boolean): ST[Unit] = {
    update(st ⇒ st.copy(confirms = st.confirms.filterNot(fn)))
  }

  private def findKeyWithName(name: String @@ KeyName): ST[Option[Key]] = {
    query(st ⇒ st.keys.find(_.name == name))
  }

  private def removeKeyIf(fn: Key ⇒ Boolean): ST[Unit] = {
    update(st ⇒ st.copy(keys = st.keys.filterNot(fn)))
  }

  private def appendNote(note: String): ST[Int] = {
    ST(st ⇒ st.copy(notes = st.notes :+ note) → st.notes.length)
  }

  private def require[A, B](t: ⇒ A)(fil: A ⇒ Boolean)(b: B): Either[ST[B], A] = {
    Try(t).filter(fil) match {
      case Success(a) ⇒ Right(a)
      case Failure(_) ⇒ Left(unit(b))
    }
  }

  private def optionalValue[A, B](t: ⇒ A)(fil: A ⇒ Boolean): Option[A] = {
    Try(t).filter(fil).toOption
  }

  private def check[B](e: Boolean)(b: B): Either[ST[B], Unit] = {
    if (e) Right(()) else Left(unit(b))
  }

  implicit def eitherToST[A, B](either: Either[ST[A], ST[A]]): ST[A] = {
    either.fold(identity, identity)
  }

  private def pass[A](a: A): Boolean = true
  private def notEmpty(a: String): Boolean = a.nonEmpty

  implicit class VectorExt[A](vec: Vector[A]) {
    def lengthOneOf(lengths: Int*): Boolean = lengths.contains(vec.length)
  }

}
