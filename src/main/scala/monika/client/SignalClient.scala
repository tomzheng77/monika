package monika.client

import com.mashape.unirest.http.Unirest
import monika.orbit.OrbitEncryption
import org.apache.commons.exec.CommandLine
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
object SignalClient extends OrbitEncryption {

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

  def parseCommand(line: String): List[String] = {
    val cmd: CommandLine = CommandLine.parse(line)
    val seq = cmd.getExecutable +: cmd.getArguments.toList
    seq.indexOf("#") match {
      case -1 ⇒ seq
      case index ⇒ seq.take(index)
    }
  }

  private var variables: Map[String, String] = Map.empty
  private var aliases: Map[String, List[String]] = Map.empty

  private var batchEnabled: Boolean = false
  private var batch: Vector[List[String]] = Vector.empty

  def exportVariable(name: String, value: String): Unit = {
    if (!name.matches("[A-Z_]+")) {
      println("variable name must match [A-Z_]+")
    } else {
      variables = variables.updated(name, value)
      println(s"$name=$value")
    }
  }

  def expandVariables(text: String): String = {
    val regex = "$[A-Z_]+".r
    val buffer = new StringBuilder()
    val a = regex.split(text).iterator
    val b = regex.findAllMatchIn(text).map(m ⇒ m.group(0))
    while (a.hasNext) {
      val x = a.next()
      buffer.append(x)
      if (b.hasNext) {
        val y = b.next()
        val yv = variables.get(y).map(s ⇒ expandVariables(s.tail)).getOrElse("")
        buffer.append(yv)
      }
    }
    buffer.toString()
  }

  def createAlias(name: String, value: List[String]): Unit = {
    aliases = aliases.updated(name, value)
    println(s"$name: $value")
  }

  def expandAlias(token: String): List[String] = {
    aliases.get(token) match {
      case None ⇒ List(token)
      case Some(expand) ⇒ expand.flatMap(expandAlias)
    }
  }

  def main(args: Array[String]): Unit = {
    setupLogger()
    while (true) {
      val optCommand: Option[List[String]] = Option(StdIn.readLine("M1-1> "))
        .map(_.trim)
        .map(parseCommand)

      optCommand match {
        case None => System.exit(0)
        case Some("exit" :: _) => System.exit(0)
        case Some("export" :: name :: value :: _) => exportVariable(name, value)
        case Some("alias" :: name :: value) => createAlias(name, value)
        case Some("echo" :: list) => println(pretty(render(list.flatMap(expandAlias).map(expandVariables))))
        case Some("batch-begin" :: _) => {
          batchEnabled = true
          batch = Vector.empty
        }
        case Some("batch-commit" :: _) => {
          batchEnabled = false
          val response: String = {
            Unirest
              .get(s"http://127.0.0.1:${Constants.InterpreterPort}/run")
              .queryString("cmd", pretty(render(batch)))
              .asString().getBody
          }
          println(response)
          batch = Vector.empty
        }
        case Some(Nil) =>
        case Some(cmd) =>
          val cmdExpanded: List[String] = cmd.flatMap(expandAlias).map(expandVariables)
          val response: String = {
            Unirest
              .get(s"http://127.0.0.1:${Constants.InterpreterPort}/run")
              .queryString("cmd", pretty(render(cmdExpanded)))
              .asString().getBody
          }
          println(response)
      }
    }
  }

}
