package monika.client

import com.mashape.unirest.http.Unirest
import org.apache.log4j._
import org.json4s.JValue
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._

import scala.io.StdIn

/**
  * - asks user for a command
  * - serializes command into JSON and sends it to the interpreter
  * - prints out the response as plain text
  * - repeat until "exit" is entered
  */
object SimpleHttpClient {

  private def setupLogger(): Unit = {
    // https://www.mkyong.com/logging/log4j-log4j-properties-examples/
    // https://stackoverflow.com/questions/8965946/configuring-log4j-loggers-programmatically
    val console = new ConsoleAppender()
    console.setLayout(new PatternLayout("%d{yyyy-MM-dd HH:mm:ss} %-5p %c:%L - %m%n"))
    console.activateOptions()

    Logger.getRootLogger.getLoggerRepository.resetConfiguration()
    Logger.getRootLogger.setLevel(Level.INFO)
    Logger.getRootLogger.addAppender(console)
  }

  def main(args: Array[String]): Unit = {
    setupLogger()
    val user = System.getenv("USER")
    assert(user != null, "user cannot be null")
    while (true) {
      val line: String = StdIn.readLine("M1-1> ").trim
      if (line == "exit") System.exit(0)

      val parts: JValue = seq2jvalue(line.split(' ').toVector)
      val partsJson: String = pretty(render(parts))
      val response: String = {
        Unirest.get(s"http://127.0.0.1:${Constants.InterpreterPort}/request")
          .queryString("user", user)
          .queryString("cmd", partsJson)
          .asString().getBody
      }
      println(response)
    }
  }

}
