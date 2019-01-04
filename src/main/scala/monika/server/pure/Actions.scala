package monika.server.pure

import java.time.LocalDateTime

import monika.server.Constants
import monika.server.Constants.CallablePrograms._
import monika.server.pure.Model._
import org.apache.log4j.Level
import org.json4s.native.JsonMethods
import org.json4s.{DefaultFormats, Extraction, Formats, JValue}
import scalaz.syntax.id._
import scalaz.{@@, Tag}

import scala.util.Try

object Actions {

  def statusReport(): Action[String] = Action((_, state) => {
    implicit val formats: Formats = DefaultFormats
    val response = JsonMethods.pretty(JsonMethods.render(Extraction.decompose(state)))
    (NIL, response, state)
  })

  def resetProfile(): Action[String] = for {
    state <- readState()
    response <- state.active match {
      case None => respond("no currently active profile")
      case Some(piq) => Action((ext, state) => (restrictProfile(ext, piq.profile), "updated profile", state))
    }
  } yield response

  /**
    * ensures: a command is generated to unlock each user
    */
  def unlockAllUsers(): Action[String] = Action((_, state) => {
    (Constants.Users.map(user => RunCommand(passwd, "-u", user)), "all users unlocked", state)
  })

  def makeBookmarks(bookmarks: Vector[Bookmark]): String = {
    ""
  }

  /**
    * updates the proxy
    * ensures: effects are generated to ensure the new profile mode
    *          is put into effect for the profile user
    */
  def restrictProfile(ext: External, profile: Profile): Vector[Effect] = {
    val mainUserGroup = s"${Constants.MainUser}:${Constants.MainUser}"
    val profileUserGroup = s"${Constants.ProfileUser}:${Constants.ProfileUser}"

    // creates effects required to setup proxy access for the profile mode
    val setupProxyAndBrowser: Vector[Effect] = Vector(
      RestartProxy(profile.proxy),
      WriteStringToFile(FilePath(Constants.Locations.ChromeBookmark), makeBookmarks(profile.bookmarks)),
      RunCommand(chown, profileUserGroup, Constants.Locations.ChromeBookmark)
    )

    // creates effects required to setup project access for the profile mode
    val setupProjectFolderPermissions: Vector[Effect] = {
      // owns the project root with main user, sets permission to 755
      val lockProjectRootFolder: Vector[Effect] = Vector(
        RunCommand(chmod, "755", Constants.Locations.ProjectRoot),
        RunCommand(chown, mainUserGroup, Constants.Locations.ProjectRoot)
      )
      // sets each project recursively to 770
      // owns each project recursively to profile user
      // owns each project root to main user
      val lockEachProjectFolder: Vector[Effect] = {
        ext.projects.values.flatMap(projPath => Vector(
          RunCommand(chmod, "-R", "770", Tag.unwrap(projPath)),
          RunCommand(chown, "-R", profileUserGroup, Tag.unwrap(projPath)),
          RunCommand(chown, mainUserGroup, Tag.unwrap(projPath))
        )).toVector
      }
      // attempts to locate each profile project in the external environment
      // if found, the folder becomes owned by the profile use
      // if not, a message is included to indicate it was not found
      val findAndUnlockEachProjectFolder: Vector[Effect] = {
        val found = profile.projects.filter(ext.projects.contains)
        val projects: Vector[String @@ FilePath] = found.map(ext.projects)
        projects.map(projPath => {
          RunCommand(chown, profileUserGroup, Tag.unwrap(projPath))
        })
      }
      // writes a line of log for every project not found
      val reportNotFoundProjects: Vector[Effect] = {
        val notFound = profile.projects.filter(ext.projects.contains)
        notFound.map(proj => WriteLog(Level.DEBUG, s"project not found: ${Tag.unwrap(proj)}"))
      }
      lockProjectRootFolder ++
      lockEachProjectFolder ++
      findAndUnlockEachProjectFolder ++
      reportNotFoundProjects
    }

    // creates effects required to setup program access
    val associateGroupsForPrograms: Vector[Effect] = {
      RunCommand(usermod, "-G", "", Constants.ProfileUser) +:
      profile.programs.map(prog => {
        RunCommand(usermod, "-a", "-G", s"use-${Tag.unwrap(prog)}", Constants.ProfileUser)
      })
    }
    setupProxyAndBrowser ++
    setupProjectFolderPermissions ++
    associateGroupsForPrograms
  }

  def applyNextProfileInQueue(): Action[String] = for {
    item <- popQueue()
    _ <- Action((ext, state) => {
      val effects = restrictProfile(ext, item.profile)
      (effects, Unit, state.copy(active = Some(item)))
    })
  } yield ""

  def clearActiveOrApplyNext(): Action[String] = for {
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
  def enqueueNextProfile(args: List[String]): Action[String] = Action((ext, state) => {
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
          def addToQueueAfter(start: LocalDateTime): ActionReturn[String] = {
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

  def reloadProfiles(profiles: Map[String @@ FileName, String]): Action[String] = Action((ext, state) => {
    val (valid: Map[String @@ FileName, JValue], _: Set[String @@ FileName]) = {
      profiles.mapValues(str => JsonMethods.parseOpt(str)).partition(pair => pair._2.isDefined) |>
        (twoMaps => (twoMaps._1.mapValues(opt => opt.get), twoMaps._2.keySet))
    }
    val newProfiles = state.profiles ++ valid.map(pair => "" -> constructProfile(pair._2, ""))
    (NIL, s"${valid.size} valid profiles found", state.copy(profiles = newProfiles))
  })

}
