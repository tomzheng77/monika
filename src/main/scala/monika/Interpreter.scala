package monika

import java.time.LocalDateTime

import monika.Profile.{MonikaState, ProfileInQueue}
import org.json4s.native.JsonMethods
import org.json4s.{DefaultFormats, Formats}
import scalaz.State
import spark.Spark

import scala.util.Try

object Interpreter {

  type ST[T] = scalaz.State[MonikaState, T]

  def addqueue_0(args: List[String], nowTime: LocalDateTime): ST[String] = State(state => {
    val profileName = args.head
    val minutes = args(1)
    if (state.queue.size >= Constants.MaxQueueSize) (state, "queue is already full")
    else if (!state.profiles.contains(profileName)) (state, s"profile not found: $profileName")
    else Try(minutes.toInt).toOption match {
      case None => (state, s"time is invalid")
      case Some(t) if t <= 0 => (state, s"time must be positive, provided $t")
      case Some(t) => {
        val profile = state.profiles(profileName)
        if (state.queue.isEmpty && state.at.isEmpty) {
          (state.copy(queue = Vector(
            ProfileInQueue(nowTime, nowTime.plusMinutes(t), profile)
          )), "successfully added")
        } else if (state.queue.isEmpty) {
          val at = state.at.get
          (state.copy(queue = Vector(
            ProfileInQueue(at.endTime, at.endTime.plusMinutes(t), profile)
          )), "successfully added")
        } else {
          val at = state.at.get
          (state.copy(queue = state.queue :+ {
            ProfileInQueue(at.endTime, at.endTime.plusMinutes(t), profile)
          }), "successfully added")
        }
        (state, s"time is invalid")
      }
    }
  })

  object Commands {
    def chkqueue(): String = ""
    def addqueue(args: List[String]): String = {
      val nowTime = LocalDateTime.now()
      if (args.length != 2) "usage: addqueue <profile> <time>"
      else Storage.transaction(state => addqueue_0(args, nowTime)(state))
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
