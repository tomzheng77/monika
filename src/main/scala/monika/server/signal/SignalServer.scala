package monika.server.signal

import monika.server.{Constants, UseJSON, UseLogger}
import spark.Spark

object SignalServer extends UseLogger with UseJSON {

  /**
    * - receives commands from the SimpleHttpClient
    * - passes them into the provided handler
    */
  def startListener(): Unit = {
    this.synchronized {
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
    val commands: Map[String, Signal] = Map(
      "brick" -> Brick,
      "set-profile" -> SetProfile,
      "show-queue" -> ShowQueue,
      "unlock" -> Unlock
    )
    commands.get(command) match {
      case None => s"unknown command '$command'"
      case Some(c) =>
        val result = c.run(args.toVector)
        result.actions
        result.futureActions
        result.message
    }
  }

}
