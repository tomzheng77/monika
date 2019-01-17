package monika.server.signal

import monika.server.action.Performer
import monika.server.{Constants, UseJSON, UseLogger, UseScalaz}
import spark.Spark

object ScriptServer extends UseLogger with UseJSON with UseScalaz {

  private val commands: Map[String, Script] = Map(
    "brick" -> Brick,
    "set-profile" -> SetProfile,
    "status" -> Status,
    "unlock" -> Unlock
  )

  /**
    * - receives commands from the SimpleHttpClient
    * - passes them into the provided handler
    */
  def startListener(): Unit = {
    this.synchronized {
      commands.foreach(pair => {
        LOGGER.debug(s"found command: ${pair._1}")
      })
      Spark.port(Constants.InterpreterPort)
      Spark.get("/request", (req, resp) => {
        resp.`type`("text/plain") // prevent being intercepted by the proxy
        val parts: List[String] = {
          val cmd: String = Option(req.queryParams("cmd")).getOrElse("")
          parseOptJSON(cmd).flatMap(_.extractOpt[List[String]]).getOrElse(Nil)
        }
        if (parts.isEmpty) "please provide a command (cmd) in JSON format"
        else performRequest(parts.head, parts.tail)
      })
    }
  }

  private def performRequest(command: String, args: List[String]): String = {
    LOGGER.debug(s"received command request: $command ${args.mkString(" ")}")
    commands.get(command) match {
      case None => s"unknown command '$command'"
      case Some(c) =>
        val result = c.run(args.toVector)
        result.actions.foreach(Performer.performAction)
        result.futureActions |> (s => Performer.enqueueAll(s))
        result.message
    }
  }

}
