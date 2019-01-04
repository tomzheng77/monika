package monika.server.pure

import java.time.LocalDateTime

import monika.server.Constants.CallablePrograms._
import monika.server.Constants._
import monika.server.pure.Model._
import monika.server.pure.ProfileActions.restrictProfile
import org.json4s.native.JsonMethods
import org.json4s.{DefaultFormats, Extraction, Formats, JValue}
import scalaz.syntax.id._
import scalaz.{@@, ReaderWriterState, Semigroup}

import scala.util.Try

object Actions {

  type Action[T] = scalaz.ReaderWriterState[External, Vector[Effect], MonikaState, T]
  type ActionReturn[T] = (Vector[Effect], T, MonikaState)
  def Action[T](f: (External, MonikaState) => (Vector[Effect], T, MonikaState)): Action[T] = ReaderWriterState.apply[External, Vector[Effect], MonikaState, T](f)
  val NIL: Vector[Nothing] = Vector.empty

  implicit object VectorSemigroup extends Semigroup[Vector[Effect]] {
    override def append(f1: Vector[Effect], f2: => Vector[Effect]): Vector[Effect] = f1 ++ f2
  }

  def readExtAndState(): Action[(External, MonikaState)] = Action((ext, state) => (NIL, (ext, state), state))
  def readState(): Action[MonikaState] = Action((_, state) => (NIL, state, state))
  def respond[T](response: T): Action[T] = Action((_, s) => (NIL, response, s))
  def popQueue(): Action[ProfileRequest] = Action((_, state) => (NIL, state.nextProfiles.head, state.copy(nextProfiles = state.nextProfiles.tail)))

  def statusReport(): Action[String] = Action((_, state) => {
    implicit val formats: Formats = DefaultFormats
    val response = JsonMethods.pretty(JsonMethods.render(Extraction.decompose(state)))
    (NIL, response, state)
  })

  def resetProfile(): Action[String] = for {
    state <- readState()
    response <- state.activeProfile match {
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
      (effects, Unit, state.copy(activeProfile = Some(item)))
    })
  } yield ""

  /**
    * ensures: any items passed the current time inside the queue are dropped
    * ensures: the active item is set to None if it has passed
    */
  def dropFromQueueAndActive(): Action[Unit] = Action((ext, state) => {
    (NIL, Unit, {
      state.copy(nextProfiles = state.nextProfiles.dropWhile(item => item.end.isBefore(ext.nowTime)),
        activeProfile = state.activeProfile.filterNot(item => item.end.isBefore(ext.nowTime)))
    })
  })

  def clearActiveOrApplyNext(): Action[String] = for {
    _ <- dropFromQueueAndActive()
    (ext, state) <- readExtAndState()
    response <- {
      if (state.activeProfile.isEmpty && state.nextProfiles.isEmpty) unlockAllUsers()
      else if (state.activeProfile.isEmpty && state.nextProfiles.head.start.isBefore(ext.nowTime)) applyNextProfileInQueue()
      else if (state.activeProfile.nonEmpty) respond("profile still active")
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
      if (state.nextProfiles.size >= MaxQueueSize) (NIL, "queue is already full", state)
      else if (!state.knownProfiles.contains(profileName)) (NIL, s"profile not found: $profileName", state)
      else Try(minutes.toInt).toOption match {
        case None => (NIL, s"time is invalid", state)
        case Some(t) if t <= 0 => (NIL, s"time must be positive, provided $t", state)
        case Some(t) => {
          val profile = state.knownProfiles(profileName)
          def addToQueueAfter(start: LocalDateTime): ActionReturn[String] = {
            (NIL, "successfully added", state.copy(nextProfiles = Vector(
              ProfileRequest(start, start.plusMinutes(t), profile)
            )))
          }
          if (state.nextProfiles.isEmpty && state.activeProfile.isEmpty) addToQueueAfter(ext.nowTime)
          else if (state.nextProfiles.isEmpty) addToQueueAfter(state.activeProfile.get.end)
          else addToQueueAfter(state.nextProfiles.last.end)
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
