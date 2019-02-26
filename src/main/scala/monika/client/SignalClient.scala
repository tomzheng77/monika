package monika.client

import com.mashape.unirest.http.Unirest
import monika.Constants
import monika.orbit.OrbitEncryption
import org.apache.commons.exec.CommandLine
import org.apache.log4j._
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

  private val VariableNameRegex = "[a-zA-Z0-9_.]+"
  private val VariableReferenceRegex = "\\$\\{" + VariableNameRegex + "\\}"

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
    line.trim() match {
      case "" ⇒ Nil
      case nonEmptyLine ⇒
        val cmd: CommandLine = CommandLine.parse(nonEmptyLine)
        val seq = cmd.getExecutable :: cmd.getArguments.toList
        val notComment = seq.indexOf("#") match {
          case -1 ⇒ seq
          case index ⇒ seq.take(index)
        }
        notComment.map(s ⇒ {
          if (s.startsWith("\"") && s.endsWith("\""))
            s.substring(1, s.length - 1) else s
        })
    }
  }

  private var variables: Map[String, String] = Map.empty
  private var aliases: Map[String, List[String]] = Map.empty
  private var signals: Vector[List[String]] = Vector.empty

  def exportVariable(name: String, value: String): Unit = {
    if (!name.matches(VariableNameRegex)) {
      println(s"variable name must match $VariableNameRegex")
    } else {
      val valueExpanded = expandVariables(value)
      variables = variables.updated(name, valueExpanded)
      println(s"$name=$valueExpanded")
    }
  }

  def expandVariables(text: String): String = {
    val regex = VariableReferenceRegex.r
    val buffer = new StringBuilder()
    val a = regex.split(text).iterator
    val b = regex.findAllMatchIn(text).map(m ⇒ m.group(0))
    while (a.hasNext || b.hasNext) {
      if (a.hasNext) {
        val x = a.next()
        buffer.append(x)
      }
      if (b.hasNext) {
        val y = b.next()
        val yx = y.substring(2, y.length - 1)
        val yv = variables.get(yx).map(expandVariables).getOrElse("")
        buffer.append(yv)
      }
    }
    buffer.toString()
  }

  def createAlias(name: String, value: List[String]): Unit = {
    aliases = aliases.updated(name, value.flatMap(expandAlias))
    println(s"$name: $value")
  }

  def expandAlias(token: String): List[String] = {
    aliases.get(token) match {
      case None ⇒ List(token)
      case Some(expand) ⇒ expand.flatMap(expandAlias)
    }
  }

  def handleBuiltin: PartialFunction[List[String], Unit] = {
    case "exit" :: Nil => System.exit(0)
    case "export" :: Nil =>
      println(variables map {
        case (key, value) ⇒ s"$key=$value"
      } mkString "\n")
    case "export" :: name :: value :: Nil => exportVariable(name, value)
    case "alias" :: Nil => {
      println(aliases map {
        case (key, value) ⇒ s"$key=${value.flatMap(expandAlias).map(expandVariables).mkString(" ")}"
      } mkString "\n")
    }
    case "alias" :: name :: value => createAlias(name, value)
    case "echo" :: list => println(list.flatMap(expandAlias).map(expandVariables).mkString(" "))
  }

  def handleOrbit: PartialFunction[List[String], Unit] = {
    case "orbit" :: args ⇒ {
      val response: String = Unirest
        .post(s"http://${Constants.OrbitAddress}:${Constants.OrbitPort}/")
        .body(encryptPBE(pretty(render(args))))
        .asString().getBody

      println(decryptPBE(response))
    }
  }

  def sendSignals(): Unit = {
    if (signals.nonEmpty) {
      val response: String = {
        Unirest
          .post(s"http://127.0.0.1:${Constants.InterpreterPort}/batch")
          .body(pretty(render(signals)))
          .asString().getBody
      }
      println(response)
    }
  }

  def main(args: Array[String]): Unit = {
    setupLogger()
    var endReached = false
    while (!endReached) {
      val prompt = if (System.console() == null) "" else "M1-1> "
      val optCommand: Option[List[String]] = Option(StdIn.readLine(prompt))
        .map(_.trim)
        .map(parseCommand)
        .map(cmd ⇒ cmd.flatMap(expandAlias).map(expandVariables))

      optCommand match {
        case None => endReached = true
        case Some(Nil) =>
        case Some(cmd) => {
          if (handleBuiltin.isDefinedAt(cmd)) handleBuiltin(cmd)
          else if (handleOrbit.isDefinedAt(cmd)) handleOrbit(cmd)
          else signals = signals :+ cmd
        }
      }
      if (System.console() != null) sendSignals()
    }
    sendSignals()
  }

}
