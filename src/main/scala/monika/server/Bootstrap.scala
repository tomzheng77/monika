package monika.server

import java.io.File
import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneOffset}
import java.util.{Timer, TimerTask}

import monika.Primitives._
import monika.server.Constants.UtilityPrograms._
import monika.server.Constants._
import monika.server.LittleProxy.ProxySettings
import monika.server.Structs._
import monika.server.Subprocess._
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.SystemUtils
import org.apache.log4j._
import org.json4s.JsonAST.JValue
import org.json4s.native.JsonMethods
import org.json4s.{DefaultFormats, Formats}
import org.slf4j.LoggerFactory
import scalaz.Tag

import scala.util.Try

object Bootstrap {

  private val LOGGER = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {
    logToFileAndConsole()
    LOGGER.info("M.O.N.I.K.A starting...")
    LOGGER.logIfFail("error while starting") {
      checkOSEnvironment()
      rejectOutgoingHttp()
      setupQueueAutomaticPoll()

      SimpleHttpServer.startWithListener(performRequest)
      val initialState: MonikaState = Persistence.readStateOrDefault()
      LittleProxy.writeCertificatesToFiles()
      LittleProxy.startOrRestart(initialState.proxy)
      setupQueueAutomaticPoll()
    }
    LOGGER.info("M.O.N.I.K.A started")
  }

  private def setupQueueAutomaticPoll(): Unit = {
    def pollQueue(): Unit = {
      LOGGER.debug("poll queue")
      Persistence.transaction(state => {
        val nowTime = LocalDateTime.now()
        state.queue.headOption match {
          case None => (state, Unit)
          case Some((time, _)) if time.isAfter(nowTime) => (state, Unit)
          case Some((_, action)) =>
            performAction(action)
            (state.copy(queue = state.queue.tail), Unit)
        }
      })
    }
    val timer = new Timer()
    timer.schedule(new TimerTask {
      override def run(): Unit = pollQueue()
    }, 0, 1000)
  }

  /**
    * -
    * - exit if a critical requirement is not met
    */
  private def checkOSEnvironment(): Unit = {
    if (!SystemUtils.IS_OS_LINUX) {
      LOGGER.error("system is not linux")
      System.exit(1)
    }
    if (System.getenv("USER") != "root") {
      LOGGER.error("user is not root")
      System.exit(2)
    }
    val cannotExecute = UtilityPrograms.filter(findProgramLocation(_).isEmpty)
    for (program <- cannotExecute) {
      val programName = Tag.unwrap(program)
      LOGGER.error(s"cannot find executable program: $programName")
    }
    if (cannotExecute.nonEmpty) {
      System.exit(3)
    }
  }

  private def performAction(action: Action): Unit = {
    LOGGER.debug(s"performing action: ${action.getClass.getSimpleName}")
    action match {
      case DisableLogin => UserControl.restrictLogin()
      case Unlock => UserControl.clearAllRestrictions()
      case other =>
    }
  }

  private val TimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
  private def brickFor(minutes: Int): String = {
    def addItemToQueue(state: MonikaState, time: LocalDateTime, action: Action): MonikaState = {
      state.copy(queue = (state.queue :+ ((time, action))).sortBy(_._1.toEpochSecond(ZoneOffset.UTC)))
    }
    UserControl.restrictLogin()
    Persistence.transaction(state => {
      val now = LocalDateTime.now()
      val timeToUnlock = now.plusMinutes(minutes).withSecond(0).withNano(0)
      val newState = addItemToQueue(state, timeToUnlock, Unlock)
      val list = newState.queue.map(item => {
        s"${item._1.format(TimeFormat)}: ${item._2}"
      }).mkString("\n")
      (newState, "successfully added to queue, queue is now:\n" + list)
    })
  }

  private def performRequest(command: String, args: List[String]): String = {
    LOGGER.debug(s"received command request: $command ${args.mkString(" ")}")
    command match {
      case "queue" =>
        Persistence.transaction(state => {
          val list = state.queue.map(item => {
            s"${item._1.format(TimeFormat)}}: ${item._2}"
          }).mkString("\n")
          (state, list)
        })
      case "brick" =>
        Try(args.head.toInt).toOption match {
          case None => "usage: brick <minutes>"
          case Some(m) if m <= 0 => "minutes must be greater than zero"
          case Some(m) => brickFor(m)
        }
      case "set-profile" =>
        val profiles = readProfileDefinitions()
        lazy val name = args.head
        if (args.isEmpty) {
          "usage: set-profile <profile>"
        } else if (!profiles.contains(name)) {
          s"cannot find profile $name, please check ${Locations.ProfileRoot}"
        } else {
          val profile = profiles(name)
          LittleProxy.startOrRestart(profile.proxy)
          UserControl.removeFromWheelGroup()
          UserControl.restrictProgramsExcept(profile.programs)
          UserControl.restrictProjectsExcept(profile.projects)
          "set-profile success"
        }
      case "unlock" =>
        UserControl.clearAllRestrictions()
        "unlock success"
      case other => s"unknown command '$other'"
    }
  }

  private def readProfileDefinitions(): Map[String, Profile] = {
    /**
      * constructs a profile from a .json definition file
      * this is not a deserialization process, it is fault tolerant and provides
      * default values for all fields except name
      */
    def convertJsonToProfile(definition: JValue): Profile = {
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
    val jsonFiles: Vector[File] = {
      val profileRoot = new File(Locations.ProfileRoot)
      if (!profileRoot.exists()) Vector.empty
      else FileUtils.listFiles(new File(Locations.ProfileRoot), Array("json"), true).asScala.toVector
    }
    val jsons = jsonFiles.map(JsonMethods.parse(_))
    jsons.map(convertJsonToProfile).map(p => p.name -> p).toMap
  }

  private def rejectOutgoingHttp(): Unit = {
    val forUser: String = MonikaUser
    call(iptables, s"-w 10 -A OUTPUT -p tcp -m owner --uid-owner $forUser --dport 80 -j REJECT".split(' '): _*)
    call(iptables, s"-w 10 -A OUTPUT -p tcp -m owner --uid-owner $forUser --dport 443 -j REJECT".split(' '): _*)
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
    Logger.getLogger("io.netty").setLevel(Level.ERROR)
  }

}
