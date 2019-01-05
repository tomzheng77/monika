package monika.server.pure

import java.time.LocalDateTime

import monika.server.Constants.CallablePrograms._
import monika.server.Constants._
import monika.server.proxy.ProxyServer.ProxySettings
import monika.server.pure.Model._
import monika.server.pure.ProfileActions.restrictProfile
import org.apache.log4j.Level
import org.json4s.native.JsonMethods
import org.json4s.{DefaultFormats, Extraction, Formats, JValue}
import scalaz.syntax.id._
import scalaz.{@@, ReaderWriterState, Semigroup}
import monika.Primitives._

import scala.util.Try

object Actions {

  /**
    * represents the external view
    * @param nowTime the current date and time
    * @param projects known projects mapped from name to path
    */
  case class ActionExternal(
    nowTime: LocalDateTime,
    projects: Map[String @@ FileName, String @@ FilePath],
    programs: Map[String @@ FileName, String @@ FilePath]
  )

  sealed trait ActionEffect
  case class RunCommand(program: String @@ FileName, args: Vector[String] = Vector.empty) extends ActionEffect
  case class RestartProxy(settings: ProxySettings) extends ActionEffect
  case class WriteStringToFile(path: String @@ FilePath, content: String) extends ActionEffect
  case class WriteLog(level: Level, message: String) extends ActionEffect
  def RunCommand(program: String @@ FileName, args: String*): RunCommand = RunCommand(program, args.toVector)

  type Action[T] = scalaz.ReaderWriterState[ActionExternal, Vector[ActionEffect], MonikaState, T]
  type ActionReturn[T] = (Vector[ActionEffect], T, MonikaState)
  def Action[T](f: (ActionExternal, MonikaState) => (Vector[ActionEffect], T, MonikaState)): Action[T] = ReaderWriterState.apply[ActionExternal, Vector[ActionEffect], MonikaState, T](f)
  val NIL: Vector[Nothing] = Vector.empty

  implicit object VectorSemigroup extends Semigroup[Vector[ActionEffect]] {
    override def append(f1: Vector[ActionEffect], f2: => Vector[ActionEffect]): Vector[ActionEffect] = f1 ++ f2
  }

  def readExtAndState(): Action[(ActionExternal, MonikaState)] = Action((ext, state) => (NIL, (ext, state), state))
  def readState(): Action[MonikaState] = Action((_, state) => (NIL, state, state))
  def respond[T](response: T): Action[T] = Action((_, s) => (NIL, response, s))
  def popQueue(): Action[ProfileRequest] = Action((_, state) => (NIL, state.queue.head, state.copy(queue = state.queue.tail)))

  def statusReport(): Action[String] = Action((_, state) => {
    implicit val formats: Formats = DefaultFormats
    val response = JsonMethods.pretty(JsonMethods.render(Extraction.decompose(state)))
    (NIL, response, state)
  })

  def resetProfile(): Action[String] = for {
    state <- readState()
    response <- state.active match {
      case None => respond("no currently active profile")
      case Some(piq) => Action((ext, state) => (restrictProfile(ext, piq.profile), "updated profile", state))
    }
  } yield response

  /**
    * ensures: a command is generated to unlock each user
    */
  def unlockAllUsers(): Action[String] = Action((_, state) => {
    (Users.map(user => RunCommand(passwd, "-u", user)), "all users unlocked", state)
  })

  def applyNextProfileInQueue(): Action[String] = for {
    item <- popQueue()
    _ <- Action((ext, state) => {
      val effects = restrictProfile(ext, item.profile)
      (effects, Unit, state.copy(active = Some(item)))
    })
  } yield ""

  /**
    * ensures: any items passed the current time inside the queue are dropped
    * ensures: the active item is set to None if it has passed
    */
  def dropFromQueueAndActive(): Action[Unit] = Action((ext, state) => {
    (NIL, Unit, {
      state.copy(queue = state.queue.dropWhile(item => item.end.isBefore(ext.nowTime)),
        active = state.active.filterNot(item => item.end.isBefore(ext.nowTime)))
    })
  })

  def clearActiveOrApplyNext(): Action[String] = for {
    _ <- dropFromQueueAndActive()
    (ext, state) <- readExtAndState()
    response <- {
      if (state.active.isEmpty && state.queue.isEmpty) unlockAllUsers()
      else if (state.active.isEmpty && state.queue.head.start.isBefore(ext.nowTime)) applyNextProfileInQueue()
      else if (state.active.nonEmpty) respond("profile still active")
      else unlockAllUsers()
    }
  } yield response

  /**
    * requires: a profile name and time passed via the arguments
    * ensures: a profile is added to the queue at the earliest feasible time
    */
  def enqueueNextProfile(args: List[String]): Action[String] = Action((ext, state) => {
    if (args.length != 2) (NIL, "usage: addqueue <profile> <time>", state)
    else {
      val profileName = args.head
      val minutes = args(1)
      if (state.queue.size >= MaxQueueSize) (NIL, "queue is already full", state)
      else if (!state.knownProfiles.contains(profileName)) (NIL, s"profile not found: $profileName", state)
      else Try(minutes.toInt).toOption match {
        case None => (NIL, s"time is invalid", state)
        case Some(t) if t <= 0 => (NIL, s"time must be positive, provided $t", state)
        case Some(t) => {
          val profile = state.knownProfiles(profileName)
          def addToQueueAfter(start: LocalDateTime): ActionReturn[String] = {
            (NIL, "successfully added", state.copy(queue = Vector(
              ProfileRequest(start, start.plusMinutes(t), profile)
            )))
          }
          if (state.queue.isEmpty && state.active.isEmpty) addToQueueAfter(ext.nowTime)
          else if (state.queue.isEmpty) addToQueueAfter(state.active.get.end)
          else addToQueueAfter(state.queue.last.end)
        }
      }
    }
  })

  def reloadProfiles(profiles: Map[String @@ FileName, String]): Action[String] = Action((ext, state) => {
    val (valid: Map[String @@ FileName, JValue], _: Set[String @@ FileName]) = {
      profiles.mapValues(str => JsonMethods.parseOpt(str)).partition(pair => pair._2.isDefined) |>
        (twoMaps => (twoMaps._1.mapValues(opt => opt.get), twoMaps._2.keySet))
    }
    val newProfiles = state.knownProfiles ++ valid.map(pair => "" -> constructProfile(pair._2, ""))
    (NIL, s"${valid.size} valid profiles found", state.copy(knownProfiles = newProfiles))
  })

}
