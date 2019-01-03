package monika

import java.time.LocalDateTime

import monika.Profile._
import org.json4s.native.JsonMethods
import org.json4s.{DefaultFormats, Extraction, Formats}
import scalaz.{@@, ReaderWriterState, Semigroup, Tag}
import spark.Spark

import scala.collection.mutable
import scala.util.Try

object Interpreter {

  sealed trait FilePath
  def FilePath[A](a: A): A @@ FilePath = Tag[A, FilePath](a)

  sealed trait Effect
  case class RunCommand(program: String, args: Vector[String] = NIL) extends Effect
  case class RestartProxy(settings: ProxySettings) extends Effect
  case class WriteStringToFile(path: String @@ FilePath, content: String) extends Effect

  /**
    * represents the external view
    * @param programs
    * @param projects
    */
  case class External(programs: Vector[Program], projects: Vector[Project])

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
    * ensures: any items past the given time inside the queue are dropped
    */
  def dropOverdueItems(time: LocalDateTime): ST[Unit] = RWS((_, state) => {
    (NIL, Unit, state.copy(queue = state.queue.dropWhile(item => item.endTime.isBefore(time))))
  })

  /**
    * requires: the queue is not empty
    * ensures: the first item of the queue is returned
    * ensures: the first item is removed from the queue
    */
  def popQueue(): ST[ProfileInQueue] = RWS((_, state) => {
    (NIL, state.queue.head, state.copy(queue = state.queue.tail))
  })

  /**
    * ensures: the state is returned
    */
  def readState(): ST[MonikaState] = RWS((_, state) => {
    (NIL, state, state)
  })

  /**
    * ensures: a command is generated to unlock each user
    */
  def unlockAllUsers(): ST[String] = RWS((_, state) => {
    (Constants.Users.map(user => RunCommand("passwd", Vector("-u", user))), "all users unlocked", state)
  })

  def makeBookmarks(bookmarks: Vector[Profile.Bookmark]): String = {
    ""
  }

  /**
    * ensures: commands are generated to ensure the new profile mode
    *          is put into effect for the profile user
    */
  def applyProfile(profile: ProfileMode): ST[String] = RWS((ext, state) => {
    // websites, projects, programs
    val mainUserGroup = s"${Constants.MainUser}:${Constants.MainUser}"
    val profileUserGroup = s"${Constants.ProfileUser}:${Constants.ProfileUser}"

    // commands required to setup proxy access for the profile mode
    def setupWebsites(): Vector[Effect] = Vector(
      RestartProxy(profile.proxy),
      WriteStringToFile(FilePath(Constants.paths.ChromeBookmark), makeBookmarks(profile.bookmarks)),
      RunCommand("chown", Vector(profileUserGroup, Constants.paths.ChromeBookmark))
    )

    // commands required to setup project access for the profile mode
    def setupProjects(): Vector[Effect] = {
      val effects = mutable.Buffer[Effect]()
      effects += RunCommand("chmod", Vector("755", Constants.paths.ProjectRoot))
      effects += RunCommand("chown", Vector(mainUserGroup, Constants.paths.ProjectRoot))
      effects ++= ext.projects.flatMap(proj => Vector(
        RunCommand("chmod", Vector("-R", "770", proj.path)),
        RunCommand("chown", Vector("-R", profileUserGroup, proj.path)),
        RunCommand("chown", Vector(mainUserGroup, proj.path))
      ))
      effects ++= profile.projects.map(proj => RunCommand("chown", Vector(profileUserGroup, proj.path)))
      effects.toVector
    }

    // commands required to setup program access for the profile mode
    def setupPrograms(): Vector[Effect] = {
      RunCommand("usermod", Vector("-G", "", Constants.ProfileUser)) +:
      profile.programs.map(prog => RunCommand("usermod", Vector("-a", "-G", s"use-${prog.name}", Constants.ProfileUser)))
    }

    (setupWebsites() ++ setupProjects() ++ setupPrograms(), "new profile applied", state)
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

  object Commands {
    def chkqueue(): String = ""
    def addqueue(args: List[String]): String = {
      val nowTime = LocalDateTime.now()
      Storage.transaction(state => {
        val (_, response, newState) = addItemToQueue(args, nowTime).run((), state)
        (newState, response)
      })
    }
    def status(): String = {
      Storage.transaction(state => {
        val (_, response, newState) = statusReport().run((), state)
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
