package monika.server.pure

import monika.server.Constants.CallablePrograms.{chmod, chown, usermod}
import monika.server.Constants.{Locations, MainUserGroup, ProfileUser, ProfileUserGroup}
import monika.server.pure.Model.{Bookmark, Effect, External, FilePath, Profile, RestartProxy, RunCommand, WriteLog, WriteStringToFile}
import org.apache.log4j.Level
import scalaz.{@@, Tag}

object ProfileActions {

  /**
    * updates the proxy
    * ensures: effects are generated to ensure the new profile mode
    *          is put into effect for the profile user
    */
  def restrictProfile(ext: External, profile: Profile): Vector[Effect] = {
    restrictWebsites(profile) ++
    restrictProjects(ext, profile) ++
    restrictPrograms(profile)
  }

  private def restrictWebsites(profile: Profile): Vector[Effect] = {
    Vector(
      RestartProxy(profile.proxy),
      WriteStringToFile(FilePath(Locations.ChromeBookmark), makeBookmarks(profile.bookmarks)),
      RunCommand(chown, ProfileUserGroup, Locations.ChromeBookmark)
    )
  }

  private def makeBookmarks(bookmarks: Vector[Bookmark]): String = {
    ""
  }

  private def restrictProjects(ext: External, profile: Profile): Vector[Effect] = {
    // owns the project root with main user, sets permission to 755
    val lockProjectRootFolder: Vector[Effect] = Vector(
      RunCommand(chmod, "755", Locations.ProjectRoot),
      RunCommand(chown, MainUserGroup, Locations.ProjectRoot)
    )
    // sets each project recursively to 770
    // owns each project recursively to profile user
    // owns each project root to main user
    val lockEachProjectFolder: Vector[Effect] = {
      ext.projects.values.flatMap(projPath => Vector(
        RunCommand(chmod, "-R", "770", Tag.unwrap(projPath)),
        RunCommand(chown, "-R", ProfileUserGroup, Tag.unwrap(projPath)),
        RunCommand(chown, MainUserGroup, Tag.unwrap(projPath))
      )).toVector
    }
    // attempts to locate each profile project in the external environment
    // if found, the folder becomes owned by the profile use
    // if not, a message is included to indicate it was not found
    val findAndUnlockEachProjectFolder: Vector[Effect] = {
      val found = profile.projects.filter(ext.projects.contains)
      val projects: Vector[String @@ FilePath] = found.map(ext.projects)
      projects.map(projPath => {
        RunCommand(chown, ProfileUserGroup, Tag.unwrap(projPath))
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

  private def restrictPrograms(profile: Profile): Vector[Effect] = {
    RunCommand(usermod, "-G", "", ProfileUser) +:
    profile.programs.map(prog => {
      RunCommand(usermod, "-a", "-G", s"use-${Tag.unwrap(prog)}", ProfileUser)
    })
  }

}
