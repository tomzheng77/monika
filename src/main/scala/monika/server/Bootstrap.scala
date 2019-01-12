package monika.server

import java.io.File

import monika.Primitives.FileName
import monika.server.Constants.{CallablePrograms, Locations, MonikaUser, RestrictedPrograms}
import monika.server.LittleProxy.ProxySettings
import monika.server.Structs.{Bookmark, MonikaState, Profile}
import monika.server.Subprocess._
import org.apache.commons.io.FileUtils
import org.apache.log4j._
import org.json4s.JsonAST.JValue
import org.json4s.native.JsonMethods
import org.json4s.{DefaultFormats, Formats}
import org.slf4j.LoggerFactory
import scalaz.Tag

object Bootstrap {

  private val LOGGER = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {
    logToFileAndConsole()
    if (System.getenv("USER") != "root") {
      LOGGER.warn("user is not root")
    }
    LOGGER.info("M.O.N.I.K.A starting...")
    checkIfProgramsAreExecutable()
    rejectOutgoingHttp()

    SimpleHttpServer.startHttpListener(handleFromClient)
    val initialState: MonikaState = Persistence.readStateOrDefault()
    LittleProxy.writeCertificatesToFiles()
    LittleProxy.startOrRestart(initialState.proxy)

    LOGGER.info("M.O.N.I.K.A started")
  }

  def handleFromClient(command: String, args: List[String]): String = {
    LOGGER.debug(s"received command request: $command ${args.mkString(" ")}")
    command match {
      case "set-profile" =>
        val profiles = listProfiles()
        val name = args.head
        val profile = profiles(name)
        LittleProxy.startOrRestart(profile.proxy)
        UserControl.removeFromWheelGroup()
        UserControl.restrictPrograms(profile.programs)
        UserControl.restrictProjects(profile.projects)
        "set-profile success"
      case "unlock" =>
        UserControl.unlock()
        "unlock success"
    }
  }

  private def checkIfProgramsAreExecutable(): Unit = {
    val programs = RestrictedPrograms ++ CallablePrograms.asList
    val cannotExecute = programs.filter(findProgramLocation(_).isEmpty)
    for (program <- cannotExecute) {
      val programName = Tag.unwrap(program)
      LOGGER.warn(s"cannot find executable program: $programName")
    }
  }

  private def listProfiles(): Map[String, Profile] = {
    /**
      * constructs a profile from a .json definition file
      * this is not a deserialization process, it is fault tolerant and provides
      * default values for all fields
      */
    def readProfileFromJSON(definition: JValue): Profile = {
      implicit val formats: Formats = DefaultFormats
      Profile(
        (definition \ "name").extract[String],
        (definition \ "programs").extractOpt[Vector[String]].getOrElse(Vector.empty).map(FileName),
        (definition \ "projects").extractOpt[Vector[String]].getOrElse(Vector.empty).map(FileName),
        (definition \ "bookmarks").extractOpt[Vector[JValue]].getOrElse(Vector.empty).map(v => {
          val url = (v \ "url").extractOpt[String].getOrElse("http://www.google.com")
          val re = "[A-Za-z-]+(\\.[A-Za-z-]+)*\\.[A-Za-z-]+".r
          val name = (v \ "name").extractOpt[String].orElse(re.findFirstIn(url)).getOrElse("Unknown")
          Bookmark(name, url)
        }),
        ProxySettings(
          (definition \ "proxy" \ "transparent").extractOpt[Boolean].getOrElse(false),
          (definition \ "proxy" \ "allowHtmlPrefix").extractOpt[Vector[String]].getOrElse(Vector.empty),
          (definition \ "proxy" \ "rejectHtmlKeywords").extractOpt[Vector[String]].getOrElse(Vector.empty)
        )
      )
    }
    import scala.collection.JavaConverters._
    val jsonFiles = FileUtils.listFiles(new File(Locations.ProfileRoot), Array("json"), true).asScala.toVector
    val jsons = jsonFiles.map(JsonMethods.parse(_))
    jsons.map(readProfileFromJSON).map(p => p.name -> p).toMap
  }

  private def rejectOutgoingHttp(): Unit = {
    val forUser: String = MonikaUser
    callWithInput("iptables", s"-w 10 -A OUTPUT -p tcp -m owner --uid-owner $forUser --dport 80 -j REJECT".split(' '))
    callWithInput("iptables", s"-w 10 -A OUTPUT -p tcp -m owner --uid-owner $forUser --dport 443 -j REJECT".split(' '))
  }

  private def logToFileAndConsole(): Unit = {
    // https://www.mkyong.com/logging/log4j-log4j-properties-examples/
    // https://stackoverflow.com/questions/8965946/configuring-log4j-loggers-programmatically
    val console = new ConsoleAppender()
    console.setLayout(new PatternLayout("%d{yyyy-MM-dd HH:mm:ss} %-5p %c:%L - %m%n"))
    console.activateOptions()

    val file = new RollingFileAppender()
    file.setFile(Locations.PrimaryLog)
    file.setMaximumFileSize(1024 * 1024 * 10)
    file.setMaxBackupIndex(10)
    file.setLayout(new PatternLayout("%d{yyyy-MM-dd HH:mm:ss} %-5p %c:%L - %m%n"))
    file.setAppend(true)
    file.activateOptions()

    Logger.getRootLogger.getLoggerRepository.resetConfiguration()
    Logger.getRootLogger.setLevel(Level.DEBUG)
    Logger.getRootLogger.addAppender(file)
    Logger.getRootLogger.addAppender(console)
    Logger.getLogger("spark.route").setLevel(Level.ERROR)
    Logger.getLogger("org.eclipse.jetty").setLevel(Level.ERROR)
  }

}
