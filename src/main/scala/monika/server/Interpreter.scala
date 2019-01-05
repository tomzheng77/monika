package monika.server

import java.io.File
import java.time.LocalDateTime

import monika.server.proxy.ProxyServer
import monika.server.pure.Actions.Action
import monika.server.pure.Model._
import org.apache.commons.io.FileUtils
import org.apache.log4j.Level
import org.json4s.native.JsonMethods
import org.json4s.{DefaultFormats, Formats}
import org.slf4j.LoggerFactory
import scalaz.syntax.id._
import scalaz.{@@, Tag}
import spark.Spark

import scala.collection.JavaConverters._

object Interpreter {

  private val LOGGER = LoggerFactory.getLogger(getClass)

  def runAction[T](rws: Action[T]): T = {
    StateStore.transaction(state => {
      val ext = composeExternal()
      val (effects, response, newState) = rws.run(ext, state)
      effects.foreach(applyEffect)
      (newState, response)
    })
  }

  /**
    * runs an HTTP command interpreter which listens for user commands
    * each command should be provided via the 'cmd' parameter, serialized in JSON
    * a response will be returned in plain text
    */
  def startHttpListener(): Unit = {
    Spark.port(Constants.InterpreterPort)
    Spark.get("/request", (req, resp) => {
      def readCommandPartsFromRequest(): List[String] = {
        implicit val formats: Formats = DefaultFormats
        val cmd: String = Option(req.queryParams("cmd")).getOrElse("")
        JsonMethods.parseOpt(cmd).flatMap(_.extractOpt[List[String]]).getOrElse(Nil)
      }

      resp.`type`("text/plain") // prevent being intercepted by the proxy
      val parts: List[String] = readCommandPartsFromRequest()
      if (parts.isEmpty) "please provide a command (cmd) in JSON format"
      else handleRequestCommand(parts.head, parts.tail)
    })
  }

  private def composeExternal(): External = {
    val projectRoot = new File(Constants.Locations.ProjectRoot)
    val projects = (projectRoot.listFiles() ?? Array())
      .filter(f => f.isDirectory)
      .map(f => (FileName(f.getName), FilePath(f.getCanonicalPath))).toMap

    External(LocalDateTime.now(), projects)
  }

  private def applyEffect(effect: Effect): Unit = {
    effect match {
      case RunCommand(program, args) => Terminal.call(Tag.unwrap(program), args.toArray)
      case RestartProxy(settings) => ProxyServer.startOrRestart(settings)
      case WriteLog(level, message) => {
        level match {
          case Level.TRACE => LOGGER.trace(message)
          case Level.DEBUG => LOGGER.debug(message)
          case Level.INFO => LOGGER.info(message)
          case Level.WARN => LOGGER.warn(message)
          case Level.ERROR => LOGGER.error(message)
          case Level.FATAL => LOGGER.error(message)
        }
      }
      case WriteStringToFile(path, content) => {
        val pathString = Tag.unwrap(path)
        FileUtils.writeStringToFile(new File(pathString), content, Constants.GlobalEncoding)
      }
    }
  }

  /**
    * handles commands from the client
    *
    * reload: reloads profile definitions into the state
    *
    * @param command name of the command
    * @param args arguments given
    * @return response message
    */
  private def handleRequestCommand(command: String, args: List[String]): String = {
    LOGGER.debug(s"received command request: $command ${args.mkString(" ")}")
    import monika.server.pure.Actions._
    command match {
      case "chkqueue" => runAction(clearActiveOrApplyNext())
      case "addqueue" => runAction(enqueueNextProfile(args))
      case "status" => runAction(statusReport())
      case "reload" => {
        val profiles = readAllProfileDefinitions()
        LOGGER.debug(s"found ${profiles.size} profile definitions")
        runAction(reloadProfiles(profiles))
      }
      case "resetprofile" => runAction(resetProfile())
      case _ => s"unknown command $command"
    }
  }

  private def readAllProfileDefinitions(): Map[String @@ FileName, String] = {
    val profileRoot = new File(Constants.Locations.ProfileRoot)
    val files = FileUtils.listFiles(profileRoot, Array("json"), true).asScala
    files.map(f => FileName(f.getName) -> FileUtils.readFileToString(f, Constants.GlobalEncoding)).toMap
  }

}
