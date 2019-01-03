package monika

import java.time.LocalDateTime

import monika.Profile.{MonikaState, ProfileInQueue, ProfileMode, ProxySettings}
import org.json4s.native.JsonMethods
import org.json4s.{DefaultFormats, Extraction, Formats}
import scalaz.ReaderWriterState
import spark.Spark

import scala.util.Try

object Interpreter {

  sealed trait Effect
  case class RunCommand(program: String, args: Vector[String]) extends Effect
  case class RestartProxy(settings: ProxySettings) extends Effect

  type ST[T] = scalaz.ReaderWriterState[Unit, List[Effect], MonikaState, T]
  type STR[T] = (List[RunCommand], T, MonikaState)
  def RWS[T] = ReaderWriterState.apply[Unit, List[Effect], MonikaState, T]

  def statusReport(): ST[String] = RWS((_, state) => {
    implicit val formats: Formats = DefaultFormats
    val response = JsonMethods.pretty(JsonMethods.render(Extraction.decompose(state)))
    (Nil, response, state)
  })

  def dropOverdueItems(time: LocalDateTime): ST[Unit] = RWS((_, state) => {
    (Nil, Unit, state.copy(queue = state.queue.dropWhile(item => item.endTime.isBefore(time))))
  })

  def popQueue(): ST[ProfileInQueue] = RWS((_, state) => {
    (Nil, state.queue.head, state.copy(queue = state.queue.tail))
  })

  def readState(): ST[MonikaState] = RWS((_, state) => {
    (Nil, state, state)
  })

  def unlockAllUsers(): ST[String] = RWS((_, state) => {
    (Constants.Users.map(user => RunCommand("passwd", Vector("-u", user))).toList, "all users unlocked", state)
  })

  def applyProfile(profile: ProfileMode): ST[String] = RWS((_, state) => {
    profile.name
    (null, "", null)
  })

  def applyNextProfileInQueue(): ST[String] = for {
    item <- popQueue()
    response <- applyProfile(item.profile)
  } yield response

  def checkQueue(nowTime: LocalDateTime): ST[String] = for {
    _ <- dropOverdueItems(nowTime)
    state <- readState()
    response <- {
      if (state.at.isEmpty && state.queue.isEmpty) unlockAllUsers()
      else if (state.at.isEmpty && state.queue.head.startTime.isBefore(nowTime)) applyNextProfileInQueue()
      else unlockAllUsers()
    }
  } yield response

  def addItemToQueue(args: List[String], nowTime: LocalDateTime): ST[String] = RWS((_, state) => {
    if (args.length != 2) (Nil, "usage: addqueue <profile> <time>", state)
    else {
      val profileName = args.head
      val minutes = args(1)
      if (state.queue.size >= Constants.MaxQueueSize) (Nil, "queue is already full", state)
      else if (!state.profiles.contains(profileName)) (Nil, s"profile not found: $profileName", state)
      else Try(minutes.toInt).toOption match {
        case None => (Nil, s"time is invalid", state)
        case Some(t) if t <= 0 => (Nil, s"time must be positive, provided $t", state)
        case Some(t) => {
          val profile = state.profiles(profileName)
          def addToQueueAfter(start: LocalDateTime): STR[String] = {
            (Nil, "successfully added", state.copy(queue = Vector(
              ProfileInQueue(start, start.plusMinutes(t), profile)
            )))
          }
          if (state.queue.isEmpty && state.at.isEmpty) addToQueueAfter(nowTime)
          else if (state.queue.isEmpty) addToQueueAfter(state.at.get.endTime)
          else addToQueueAfter(state.queue.last.endTime)
        }
      }
    }
  })

  object Commands {
    def chkqueue(): String = ""
    def addqueue(args: List[String]): String = {
      val nowTime = LocalDateTime.now()
      Storage.transaction(state => {
        val (_, response, newState) = addItemToQueue(args, nowTime).run(null, state)
        (newState, response)
      })
    }
    def status(): String = {
      Storage.transaction(state => {
        val (_, response, newState) = statusReport().run(null, state)
        (newState, response)
      })
    }
    def resetprofile(): String = ""
  }

  def handleRequestCommand(name: String, args: List[String]): String = {
    import Commands._
    name match {
      case "chkqueue" => chkqueue()
      case "addqueue" => addqueue(args)
      case "status" => status()
      case "resetprofile" => resetprofile()
      case _ => s"unknown command $name"
    }
  }

  /**
    * runs an HTTP command interpreter which listens for user commands
    * each command should be provided via the 'cmd' parameter, serialized in JSON
    * a response will be returned in plain text
    */
  def startHttpServer(): Unit = {
    Spark.port(Constants.InterpreterPort)
    Spark.get("/request", (req, resp) => {
      resp.`type`("text/plain") // change to anything but text/html to prevent being intercepted by the proxy
      implicit val formats: Formats = DefaultFormats
      val cmd: String = Option(req.queryParams("cmd")).getOrElse("")
      val parts: List[String] = JsonMethods.parseOpt(cmd).flatMap(_.extractOpt[List[String]]).getOrElse(Nil)
      if (parts.isEmpty) "please provide a command (cmd) in JSON format"
      else handleRequestCommand(parts.head, parts.tail)
    })
  }

}
