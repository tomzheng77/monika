package monika.server

import java.io.File
import java.time.LocalDateTime

import monika.server.Constants.programs
import monika.server.Model._
import org.apache.commons.io.FileUtils
import org.json4s.native.JsonMethods
import org.json4s.{DefaultFormats, Extraction, Formats, JValue}
import scalaz.{@@, Tag}
import scalaz.syntax.id._
import spark.Spark

import scala.collection.mutable
import scala.util.Try
import scala.collection.JavaConverters._

object Interpreter {

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
    (Constants.Users.map(user => RunCommand(programs.passwd, Vector("-u", user))), "all users unlocked", state)
  })

  def makeBookmarks(bookmarks: Vector[Bookmark]): String = {
    ""
  }

  /**
    * updates the proxy
    * ensures: effects are generated to ensure the new profile mode
    *          is put into effect for the profile user
    */
  def addEffectsForProfile(profile: ProfileMode): RWS[String] = RWS((ext, state) => {
    // websites, projects, programs
    val mainUserGroup = s"${Constants.MainUser}:${Constants.MainUser}"
    val profileUserGroup = s"${Constants.ProfileUser}:${Constants.ProfileUser}"
    val outMessage = new mutable.StringBuilder()

    // creates effects required to setup proxy access for the profile mode
    def setupProxyAndBrowser(): Vector[Effect] = Vector(
      RestartProxy(profile.proxy),
      WriteStringToFile(FilePath(Constants.paths.ChromeBookmark), makeBookmarks(profile.bookmarks)),
      RunCommand(programs.chown, Vector(profileUserGroup, Constants.paths.ChromeBookmark))
    )

    // creates effects required to setup project access for the profile mode
    def setupProjectFolderPermissions(): Vector[Effect] = {
      val effects = mutable.Buffer[Effect]()

      // owns the project root with main user, sets permission to 755
      def lockProjectRootFolder(): Unit = {
        effects += RunCommand(programs.chmod, Vector("755", Constants.paths.ProjectRoot))
        effects += RunCommand(programs.chown, Vector(mainUserGroup, Constants.paths.ProjectRoot))
      }

      // sets each project recursively to 770
      // owns each project recursively to profile user
      // owns each project root to main user
      def lockEachProjectFolder(): Unit = {
        effects ++= ext.projects.values.flatMap(projPath => Vector(
          RunCommand(programs.chmod, Vector("-R", "770", Tag.unwrap(projPath))),
          RunCommand(programs.chown, Vector("-R", profileUserGroup, Tag.unwrap(projPath))),
          RunCommand(programs.chown, Vector(mainUserGroup, Tag.unwrap(projPath)))
        ))
      }

      // attempts to locate each profile project in the external environment
      // if found, the folder becomes owned by the profile use
      // if not, a message is included to indicate it was not found
      def findAndUnlockProjectFolder(): Unit = {
        val (found, notFound) = profile.projects.partition(ext.projects.contains)
        val projects: Vector[String @@ FilePath] = found.map(ext.projects)
        effects ++= projects.map(projPath => {
          RunCommand(programs.chown, Vector(profileUserGroup, Tag.unwrap(projPath)))
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
      RunCommand(programs.usermod, Vector("-G", "", Constants.ProfileUser)) +:
      profile.programs.map(prog => {
        RunCommand(programs.usermod, Vector("-a", "-G", s"use-${Tag.unwrap(prog)}", Constants.ProfileUser))
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
    val projectRoot = new File(Constants.paths.ProjectRoot)
    val projects = (projectRoot.listFiles() ?? Array())
      .filter(f => f.isDirectory)
      .map(f => (FileName(f.getName), FilePath(f.getCanonicalPath))).toMap

    External(LocalDateTime.now(), projects)
  }

  def applyEffects(effects: Vector[Effect]): Unit = {
    for (effect <- effects) {
      println(effect)
    }
  }

  def runTransaction(rws: RWS[String]): String = {
    Storage.transaction(state => {
      val ext = listEnvironment()
      val (effects, response, newState) = rws.run(ext, state)
      applyEffects(effects)
      (newState, response)
    })
  }

  def readProfilesAsString(): Map[String @@ FileName, String] = {
    val profileRoot = new File(Constants.paths.ProfileRoot)
    val files = FileUtils.listFiles(profileRoot, Array("json"), true).asScala
    files.map(f => FileName(f.getName) -> FileUtils.readFileToString(f, Constants.GlobalEncoding)).toMap
  }

  def handleRequestCommand(name: String, args: List[String]): String = {
    name match {
      case "chkqueue" => runTransaction(clearActiveOrApplyNext())
      case "addqueue" => runTransaction(enqueueNextProfile(args))
      case "status" => runTransaction(statusReport())
      case "reload" => {
        val profiles = readProfilesAsString()
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
  def startHttpServer(): Unit = {
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
