package monika.orbit

import java.security.SecureRandom
import java.time.LocalDateTime

import org.json4s.{DefaultFormats, JValue}
import scalaz.effect.IO
import scalaz.{@@, State, Tag}
import org.json4s.JsonDSL._

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
  def STA[A](a: A): ST[A] = State.state(a)

  def initialState: IO[OrbitState] = IO {
    val random = new SecureRandom()
    OrbitState(random.nextInt(), Map.empty, Map.empty)
  }

  def ofMessage(message: String): JValue = "message" → message

  def handle(request: JValue): ST[JValue] = {
    implicit val formats = DefaultFormats
    (request \ "command").extractOpt[String].getOrElse("").trim match {
      case "" ⇒ STA(ofMessage("please provide a command"))
      case "add-key" ⇒ STA(ofMessage("please provide a command"))
      case "add-confirm" ⇒ STA(ofMessage("please provide a command"))
      case "confirm" ⇒ STA(ofMessage("please provide a command"))
      case "orbit" ⇒ STA(ofMessage("please provide a command"))
      case other ⇒ STA(ofMessage(s"command '$other' is not recognised"))
    }

    ST(state ⇒ {
      null
    })
  }

  def confirm(name: String): ST[Unit] = ST(state ⇒ {
    if (state.confirms.contains(ConfirmName(name))) null
    null
  })

}
