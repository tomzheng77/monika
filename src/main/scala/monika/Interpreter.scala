package monika

import java.time.LocalDateTime

import monika.Profile.{MonikaState, ProfileInQueue, ProfileMode}
import org.json4s.native.JsonMethods
import org.json4s.{DefaultFormats, Formats}
import scalaz.ReaderWriterState
import spark.Spark

import scala.util.Try

object Interpreter {

  case class RunCommand(program: String, args: Vector[String])
  type ST[T] = scalaz.ReaderWriterState[Unit, List[RunCommand], MonikaState, T]
  type STR[T] = (List[RunCommand], T, MonikaState)
  def RWS = ReaderWriterState

  def addqueue_transaction(args: List[String], nowTime: LocalDateTime): ST[String] = RWS((_, state) => {
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
          def addToQueueStartingAt(start: LocalDateTime): STR[String] = {
            val profile = state.profiles(profileName)
            (Nil, "successfully added", state.copy(queue = Vector(
              ProfileInQueue(start, start.plusMinutes(t), profile)
            )))
          }
          if (state.queue.isEmpty && state.at.isEmpty) addToQueueStartingAt(nowTime)
          else if (state.queue.isEmpty) addToQueueStartingAt(state.at.get.endTime)
          else addToQueueStartingAt(state.queue.last.endTime)
        }
      }
    }
  })

  object Commands {
    def chkqueue(): String = ""
    def addqueue(args: List[String]): String = {
      val nowTime = LocalDateTime.now()
      Storage.transaction(state => {
        val (_, response, newState) = addqueue_transaction(args, nowTime).run(null, state)
        (newState, response)
      })
    }
    def status(): String = ""
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
