package monika

import java.io.File
import java.time.LocalDateTime

import monika.Profile._
import org.json4s.native.JsonMethods
import org.json4s.{DefaultFormats, Extraction, Formats}
import scalaz.{@@, ReaderWriterState, Semigroup, Tag}
import scalaz.syntax.id._
import spark.Spark

import scala.collection.mutable
import scala.util.Try

object Interpreter {

  sealed trait Effect
  case class RunCommand(program: String, args: Vector[String] = NIL) extends Effect
  case class RestartProxy(settings: ProxySettings) extends Effect
  case class WriteStringToFile(path: String @@ FilePath, content: String) extends Effect

  /**
    * represents the external view
    * @param projects known projects mapped from name to path
    */
  case class External(
    nowTime: LocalDateTime,
    projects: Map[String @@ FileName, String @@ FilePath]
  )

  type ST[T] = scalaz.ReaderWriterState[External, Vector[Effect], MonikaState, T]
  type STR[T] = (Vector[Effect], T, MonikaState)
  def RWS[T](f: (External, MonikaState) => (Vector[Effect], T, MonikaState)) = ReaderWriterState.apply[External, Vector[Effect], MonikaState, T](f)
  val NIL = Vector.empty
  
  def statusReport(): ST[String] = RWS((_, state) => {
    implicit val formats: Formats = DefaultFormats
    val response = JsonMethods.pretty(JsonMethods.render(Extraction.decompose(state)))
    (NIL, response, state)
  })

  /**
    * ensures: a command is generated to unlock each user
    */
  def unlockAllUsers(): ST[String] = RWS((_, state) => {
    (Constants.Users.map(user => RunCommand("passwd", Vector("-u", user))), "all users unlocked", state)
  })

  def makeBookmarks(bookmarks: Vector[Bookmark]): String = {
    ""
  }

  /**
    * updates the proxy
    * ensures: commands are generated to ensure the new profile mode
    *          is put into effect for the profile user
    */
  def applyProfile(profile: ProfileMode): ST[String] = RWS((ext, state) => {
    // websites, projects, programs
    val mainUserGroup = s"${Constants.MainUser}:${Constants.MainUser}"
    val profileUserGroup = s"${Constants.ProfileUser}:${Constants.ProfileUser}"
    val outMessage = new mutable.StringBuilder()

    // creates effects required to setup proxy access for the profile mode
    def setupProxyAndBrowser(): Vector[Effect] = Vector(
      RestartProxy(profile.proxy),
      WriteStringToFile(FilePath(Constants.paths.ChromeBookmark), makeBookmarks(profile.bookmarks)),
      RunCommand("chown", Vector(profileUserGroup, Constants.paths.ChromeBookmark))
    )

    // creates effects required to setup project access for the profile mode
    def setupProjectFolderPermissions(): Vector[Effect] = {
      val effects = mutable.Buffer[Effect]()

      // owns the project root with main user, sets permission to 755
      def lockProjectRootFolder(): Unit = {
        effects += RunCommand("chmod", Vector("755", Constants.paths.ProjectRoot))
        effects += RunCommand("chown", Vector(mainUserGroup, Constants.paths.ProjectRoot))
      }

      // sets each project recursively to 770
      // owns each project recursively to profile user
      // owns each project root to main user
      def lockEachProjectFolder(): Unit = {
        effects ++= ext.projects.values.flatMap(projPath => Vector(
          RunCommand("chmod", Vector("-R", "770", Tag.unwrap(projPath))),
          RunCommand("chown", Vector("-R", profileUserGroup, Tag.unwrap(projPath))),
          RunCommand("chown", Vector(mainUserGroup, Tag.unwrap(projPath)))
        ))
      }

      // attempts to locate each profile project in the external environment
      // if found, the folder becomes owned by the profile use
      // if not, a message is included to indicate it was not found
      def findAndUnlockProjectFolder(): Unit = {
        val (found, notFound) = profile.projects.partition(ext.projects.contains)
        val projects: Vector[String @@ FilePath] = found.map(ext.projects)
        effects ++= projects.map(projPath => {
          RunCommand("chown", Vector(profileUserGroup, Tag.unwrap(projPath)))
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
      RunCommand("usermod", Vector("-G", "", Constants.ProfileUser)) +:
      profile.programs.map(prog => {
        RunCommand("usermod", Vector("-a", "-G", s"use-${Tag.unwrap(prog)}", Constants.ProfileUser))
      })
    }

    val allEffects = setupProxyAndBrowser() ++ setupProjectFolderPermissions() ++ associateGroupsForPrograms()
    (allEffects, outMessage.toString(), state)
  })

  implicit val semi: Semigroup[Vector[Effect]] = new Semigroup[Vector[Effect]] {
    override def append(f1: Vector[Effect], f2: => Vector[Effect]): Vector[Effect] = f1 ++ f2
  }

  def applyNextProfileInQueue(): ST[String] = for {
    item <- popQueue()
    response <- applyProfile(item.profile)
  } yield response

  def checkQueue(nowTime: LocalDateTime): ST[String] = for {
    _ <- dropOverdueItems(nowTime)
    state <- readState()
    response <- {
      if (state.at.isEmpty && state.queue.isEmpty) unlockAllUsers()
      else if (state.at.isEmpty && state.queue.head.startTime.isBefore(nowTime)) applyNextProfileInQueue()
      else if (state.at.nonEmpty) applyNextProfileInQueue()
      else unlockAllUsers()
    }
  } yield response

  def addItemToQueue(args: List[String], nowTime: LocalDateTime): ST[String] = RWS((_, state) => {
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
          if (state.queue.isEmpty && state.at.isEmpty) addToQueueAfter(nowTime)
          else if (state.queue.isEmpty) addToQueueAfter(state.at.get.endTime)
          else addToQueueAfter(state.queue.last.endTime)
        }
      }
    }
  })

  def listEnvironment(): External = {
    val projectRoot = new File(Constants.paths.ProjectRoot)
    val projects = (projectRoot.listFiles() ?? Array())
      .filter(f => f.isDirectory)
      .map(f => (FileName(f.getName), FilePath(f.getCanonicalPath))).toMap

    External(LocalDateTime.now(), projects)
  }

  object Commands {
    def chkqueue(): String = ""
    def addqueue(args: List[String]): String = {
      val nowTime = LocalDateTime.now()
      Storage.transaction(state => {
        val ext = listEnvironment()
        val (_, response, newState) = addItemToQueue(args, nowTime).run(ext, state)
        (newState, response)
      })
    }
    def status(): String = {
      Storage.transaction(state => {
        val ext = listEnvironment()
        val (_, response, newState) = statusReport().run(ext, state)
        (newState, response)
      })
    }
    def resetprofile(): String = ""
  }

  def handleRequestCommand(name: String, args: List[String]): String = {
    import Commands._
    name match {
      case "chkqueue" => chkqueue()
      case "addqueue" => addqueue(args)
      case "status" => status()
      case "resetprofile" => resetprofile()
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
