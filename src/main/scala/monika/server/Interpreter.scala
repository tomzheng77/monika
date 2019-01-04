package monika.server

import java.io.File
import java.time.LocalDateTime

import monika.proxy.ProxyServer
import monika.server.Constants.CallablePrograms
import monika.server.Model._
import org.apache.commons.io.FileUtils
import org.json4s.native.JsonMethods
import org.json4s.{DefaultFormats, Extraction, Formats, JValue}
import org.slf4j.LoggerFactory
import scalaz.syntax.id._
import scalaz.{@@, Tag}
import spark.Spark

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.Try

object Interpreter {

  private val LOGGER = LoggerFactory.getLogger(getClass)

  def statusReport(): RWS[String] = RWS((_, state) => {
    implicit val formats: Formats = DefaultFormats
    val response = JsonMethods.pretty(JsonMethods.render(Extraction.decompose(state)))
    (NIL, response, state)
  })

  def respond(response: String): RWS[String] = RWS((_, s) => (NIL, response, s))

  def resetProfile(): RWS[String] = for {
    state <- readState()
    response <- state.active match {
      case None => respond("no currently active profile")
      case Some(piq) => addEffectsForProfile(piq.profile)
    }
  } yield response

  /**
    * ensures: a command is generated to unlock each user
    */
  def unlockAllUsers(): RWS[String] = RWS((_, state) => {
    (Constants.Users.map(user => RunCommand(CallablePrograms.passwd, Vector("-u", user))), "all users unlocked", state)
  })

  def makeBookmarks(bookmarks: Vector[Bookmark]): String = {
    ""
  }

  /**
    * updates the proxy
    * ensures: effects are generated to ensure the new profile mode
    *          is put into effect for the profile user
    */
  def addEffectsForProfile(profile: Profile): RWS[String] = RWS((ext, state) => {
    // websites, projects, programs
    val mainUserGroup = s"${Constants.MainUser}:${Constants.MainUser}"
    val profileUserGroup = s"${Constants.ProfileUser}:${Constants.ProfileUser}"
    val outMessage = new mutable.StringBuilder()

    // creates effects required to setup proxy access for the profile mode
    def setupProxyAndBrowser(): Vector[Effect] = Vector(
      RestartProxy(profile.proxy),
      WriteStringToFile(FilePath(Constants.Locations.ChromeBookmark), makeBookmarks(profile.bookmarks)),
      RunCommand(CallablePrograms.chown, Vector(profileUserGroup, Constants.Locations.ChromeBookmark))
    )

    // creates effects required to setup project access for the profile mode
    def setupProjectFolderPermissions(): Vector[Effect] = {
      val effects = mutable.Buffer[Effect]()

      // owns the project root with main user, sets permission to 755
      def lockProjectRootFolder(): Unit = {
        effects += RunCommand(CallablePrograms.chmod, Vector("755", Constants.Locations.ProjectRoot))
        effects += RunCommand(CallablePrograms.chown, Vector(mainUserGroup, Constants.Locations.ProjectRoot))
      }

      // sets each project recursively to 770
      // owns each project recursively to profile user
      // owns each project root to main user
      def lockEachProjectFolder(): Unit = {
        effects ++= ext.projects.values.flatMap(projPath => Vector(
          RunCommand(CallablePrograms.chmod, Vector("-R", "770", Tag.unwrap(projPath))),
          RunCommand(CallablePrograms.chown, Vector("-R", profileUserGroup, Tag.unwrap(projPath))),
          RunCommand(CallablePrograms.chown, Vector(mainUserGroup, Tag.unwrap(projPath)))
        ))
      }

      // attempts to locate each profile project in the external environment
      // if found, the folder becomes owned by the profile use
      // if not, a message is included to indicate it was not found
      def findAndUnlockProjectFolder(): Unit = {
        val (found, notFound) = profile.projects.partition(ext.projects.contains)
        val projects: Vector[String @@ FilePath] = found.map(ext.projects)
        effects ++= projects.map(projPath => {
          RunCommand(CallablePrograms.chown, Vector(profileUserGroup, Tag.unwrap(projPath)))
        })
        outMessage append notFound.map(projName => s"project not found: $projName\n").mkString
      }

      lockProjectRootFolder()
      lockEachProjectFolder()
      findAndUnlockProjectFolder()
      effects.toVector
    }

    // creates effects required to setup program access
    def associateGroupsForPrograms(): Vector[Effect] = {
      RunCommand(CallablePrograms.usermod, Vector("-G", "", Constants.ProfileUser)) +:
      profile.programs.map(prog => {
        RunCommand(CallablePrograms.usermod, Vector("-a", "-G", s"use-${Tag.unwrap(prog)}", Constants.ProfileUser))
      })
    }

    val allEffects = setupProxyAndBrowser() ++ setupProjectFolderPermissions() ++ associateGroupsForPrograms()
    (allEffects, outMessage.toString(), state)
  })

  def applyNextProfileInQueue(): RWS[String] = for {
    item <- popQueue()
    response <- addEffectsForProfile(item.profile)
    _ <- RWS((_, state) => {
      (NIL, null, state.copy(active = Some(item)))
    })
  } yield response

  def clearActiveOrApplyNext(): RWS[String] = for {
    _ <- dropFromQueueAndActive()
    (ext, state) <- readExtAndState()
    response <- {
      if (state.active.isEmpty && state.queue.isEmpty) unlockAllUsers()
      else if (state.active.isEmpty && state.queue.head.startTime.isBefore(ext.nowTime)) applyNextProfileInQueue()
      else if (state.active.nonEmpty) respond("profile still active")
      else unlockAllUsers()
    }
  } yield response

  /**
    * requires: a profile name and time passed via the arguments
    * ensures: a profile is added to the queue at the earliest feasible time
    */
  def enqueueNextProfile(args: List[String]): RWS[String] = RWS((ext, state) => {
    if (args.length != 2) (NIL, "usage: addqueue <profile> <time>", state)
    else {
      val profileName = args.head
      val minutes = args(1)
      if (state.queue.size >= Constants.MaxQueueSize) (NIL, "queue is already full", state)
      else if (!state.profiles.contains(profileName)) (NIL, s"profile not found: $profileName", state)
      else Try(minutes.toInt).toOption match {
        case None => (NIL, s"time is invalid", state)
        case Some(t) if t <= 0 => (NIL, s"time must be positive, provided $t", state)
        case Some(t) => {
          val profile = state.profiles(profileName)
          def addToQueueAfter(start: LocalDateTime): STR[String] = {
            (NIL, "successfully added", state.copy(queue = Vector(
              ProfileInQueue(start, start.plusMinutes(t), profile)
            )))
          }
          if (state.queue.isEmpty && state.active.isEmpty) addToQueueAfter(ext.nowTime)
          else if (state.queue.isEmpty) addToQueueAfter(state.active.get.endTime)
          else addToQueueAfter(state.queue.last.endTime)
        }
      }
    }
  })

  def reloadProfiles(profiles: Map[String @@ FileName, String]): RWS[String] = RWS((ext, state) => {
    val (valid: Map[String @@ FileName, JValue], notValid: Set[String @@ FileName]) = {
      profiles.mapValues(str => JsonMethods.parseOpt(str)).partition(pair => pair._2.isDefined) |>
        (twoMaps => (twoMaps._1.mapValues(opt => opt.get), twoMaps._2.keySet))
    }
    val newProfiles = state.profiles ++ valid.map(pair => "" -> constructProfile(pair._2, ""))
    (NIL, s"${valid.size} valid profiles found", state.copy(profiles = newProfiles))
  })

  def listEnvironment(): External = {
    val projectRoot = new File(Constants.Locations.ProjectRoot)
    val projects = (projectRoot.listFiles() ?? Array())
      .filter(f => f.isDirectory)
      .map(f => (FileName(f.getName), FilePath(f.getCanonicalPath))).toMap

    External(LocalDateTime.now(), projects)
  }

  def applyEffects(effects: Vector[Effect]): Unit = {
    for (effect <- effects) effect match {
      case RunCommand(program, args) => Environment.call(Tag.unwrap(program), args.toArray)
      case RestartProxy(settings) => ProxyServer.startOrRestart(settings)
      case WriteStringToFile(path, content) =>
        val pathString = Tag.unwrap(path)
        FileUtils.writeStringToFile(new File(pathString), content, Constants.GlobalEncoding)
    }
  }

  def runTransaction(rws: RWS[String]): String = {
    StateSingleton.transaction(state => {
      val ext = listEnvironment()
      val (effects, response, newState) = rws.run(ext, state)
      applyEffects(effects)
      (newState, response)
    })
  }

  def readProfileDefinitions(): Map[String @@ FileName, String] = {
    val profileRoot = new File(Constants.Locations.ProfileRoot)
    val files = FileUtils.listFiles(profileRoot, Array("json"), true).asScala
    files.map(f => FileName(f.getName) -> FileUtils.readFileToString(f, Constants.GlobalEncoding)).toMap
  }

  def handleRequestCommand(name: String, args: List[String]): String = {
    LOGGER.debug(s"received command request: $name ${args.mkString(" ")}")
    name match {
      case "chkqueue" => runTransaction(clearActiveOrApplyNext())
      case "addqueue" => runTransaction(enqueueNextProfile(args))
      case "status" => runTransaction(statusReport())
      case "reload" => {
        val profiles = readProfileDefinitions()
        LOGGER.debug(s"found ${profiles.size} profile definitions")
        runTransaction(reloadProfiles(profiles))
      }
      case "resetprofile" => runTransaction(resetProfile())
      case _ => s"unknown command $name"
    }
  }

  /**
    * runs an HTTP command interpreter which listens for user commands
    * each command should be provided via the 'cmd' parameter, serialized in JSON
    * a response will be returned in plain text
    */
  def startHttpListener(): Unit = {
    Spark.port(Constants.InterpreterPort)
    Spark.get("/request", (req, resp) => {
      resp.`type`("text/plain") // change to anything but text/html to prevent being intercepted by the proxy
      implicit val formats: Formats = DefaultFormats
      val cmd: String = Option(req.queryParams("cmd")).getOrElse("")
      val parts: List[String] = JsonMethods.parseOpt(cmd).flatMap(_.extractOpt[List[String]]).getOrElse(Nil)
      if (parts.isEmpty) "please provide a command (cmd) in JSON format"
      else handleRequestCommand(parts.head, parts.tail)
    })
  }

}
