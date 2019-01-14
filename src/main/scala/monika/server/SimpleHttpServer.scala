package monika.server

import org.json4s.native.JsonMethods
import org.json4s.{DefaultFormats, Formats}
import spark.Spark

object SimpleHttpServer {

  /**
    * runs an HTTP command interpreter which listens for user commands
    * each command should be provided via the 'cmd' parameter, serialized in JSON
    * a response will be returned in plain text
    */
  def startWithListener(handler: (String, List[String]) => String): Unit = {
    this.synchronized {
      Spark.port(Constants.InterpreterPort)
      Spark.get("/request", (req, resp) => {
        resp.`type`("text/plain") // prevent being intercepted by the proxy
        val parts: List[String] = {
          implicit val formats: Formats = DefaultFormats
          val cmd: String = Option(req.queryParams("cmd")).getOrElse("")
          JsonMethods.parseOpt(cmd).flatMap(_.extractOpt[List[String]]).getOrElse(Nil)
        }
        if (parts.isEmpty) "please provide a command (cmd) in JSON format"
        else handler(parts.head, parts.tail)
      })
    }
  }

}
