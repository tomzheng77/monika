package monika.server

import spark.Spark

object SimpleHttpServer extends UseJSON {

  /**
    * - receives commands from the SimpleHttpClient
    * - passes them into the provided handler
    */
  def startWithListener(handler: (String, List[String]) => String): Unit = {
    this.synchronized {
      Spark.port(Constants.InterpreterPort)
      Spark.get("/request", (req, resp) => {
        resp.`type`("text/plain") // prevent being intercepted by the proxy
        val parts: List[String] = {
          val cmd: String = Option(req.queryParams("cmd")).getOrElse("")
          parseOptJSON(cmd).flatMap(_.extractOpt[List[String]]).getOrElse(Nil)
        }
        if (parts.isEmpty) "please provide a command (cmd) in JSON format"
        else handler(parts.head, parts.tail)
      })
    }
  }

}
