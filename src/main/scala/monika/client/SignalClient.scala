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

  private var batchEnabled: Boolean = false
  private var batch: Vector[List[String]] = Vector.empty

  private val VariableNameRegex = "[a-zA-Z0-9_.]+"
  private val VariableReferenceRegex = "\\$\\{" + VariableNameRegex + "\\}"

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
    case "batch-begin" :: Nil => {
      batchEnabled = true
      batch = Vector.empty
    }
    case "batch-commit" :: Nil => {
      batchEnabled = false
      val response: String = {
        Unirest
          .post(s"http://127.0.0.1:${Constants.InterpreterPort}/batch")
          .body(pretty(render(batch)))
          .asString().getBody
      }
      println(response)
      batch = Vector.empty
    }
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

  def handleScript(cmd: List[String]): Unit = {
    val cmdExpanded: List[String] = cmd.flatMap(expandAlias).map(expandVariables)
    if (batchEnabled) batch = batch :+ cmdExpanded
    else {
      val response: String = {
        Unirest
          .get(s"http://127.0.0.1:${Constants.InterpreterPort}/run")
          .queryString("cmd", pretty(render(cmdExpanded)))
          .asString().getBody
      }
      println(response)
    }
  }

  def main(args: Array[String]): Unit = {
    setupLogger()
    while (true) {
      val prompt = (System.console(), batchEnabled) match {
        case (null, _) ⇒ ""
        case (_, true) ⇒ "    > "
        case _ ⇒ "M1-1> "
      }
      val optCommand: Option[List[String]] = Option(StdIn.readLine(prompt))
        .map(_.trim)
        .map(parseCommand)

      optCommand match {
        case None => System.exit(0)
        case Some(Nil) =>
        case Some(cmd) => {
          if (handleBuiltin.isDefinedAt(cmd)) handleBuiltin(cmd)
          else if (handleOrbit.isDefinedAt(cmd)) handleOrbit(cmd)
          else handleScript(cmd)
        }
      }
    }
  }

}
