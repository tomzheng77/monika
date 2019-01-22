package monika.server.script.library

import java.io.File

import monika.Primitives.FileName
import monika.server.script.Script
import monika.server.subprocess.Commands._
import monika.server.{Constants, UseScalaz}
import scalaz.{@@, Tag}

trait RestrictionOps extends UseScalaz with ReaderOps { self: Script =>

  // the environment of this user will be affected
  private val User = Constants.MonikaUser

  def restrictLogin(): SC[Unit] = SC(api => {
    api.call(passwd, "-l", User)
  })

  def forceLogout(): SC[Unit] = SC(api => {
    api.call(killall, "-u", User, "i3")
    api.call(killall, "-u", User, "gnome-session-binary")
  })

  def restrictProgramsExcept(except: Vector[String @@ FileName]): SC[Unit] = SC(api => {
    val (toUnlock, toLock) = Constants.Restricted.Programs
      .partition(pair => except.contains(pair._1)) |>
      (pair => (
        pair._1.flatMap(x => api.findExecutableInPath(x._1) ++ x._2),
        pair._2.flatMap(x => api.findExecutableInPath(x._1) ++ x._2)
      ))

    for (program <- toUnlock) {
      api.call(chmod, "755", Tag.unwrap(program))
      api.call(chown, "root:root", Tag.unwrap(program))
    }
    for (program <- toLock) {
      api.call(killall, "-u", User, Tag.unwrap(program))
      api.call(chmod, "700", Tag.unwrap(program))
      api.call(chown, "root:root", Tag.unwrap(program))
    }
  })

  def restrictProjectsExcept(except: Vector[String @@ FileName]): SC[Unit] = SC(api => {
    val projectNameSet = except.map(Tag.unwrap).toSet
    val projects = Constants.Restricted.Projects.map(Tag.unwrap).map(new File(_)).flatMap(f => f.listFiles() ?? Array.empty)
    val (toUnlock, toLock) = projects.partition(f => projectNameSet contains f.getName)

    for (projectLocation <- Constants.Restricted.Projects) {
      api.call(chown, "root:root", Tag.unwrap(projectLocation))
      api.call(chmod, "777", Tag.unwrap(projectLocation))
    }
    for (project <- toUnlock) {
      api.call(chmod, "755", project.getCanonicalPath)
      api.call(chown, s"$User:$User", project.getCanonicalPath)
    }
    for (project <- toLock) {
      api.call(chmod, "700", project.getCanonicalPath)
      api.call(chown, "root:root", project.getCanonicalPath)
    }
  })

  /**
    * clears any restrictions previously applied on the user
    * i.e. sudo, programs, projects
    */
  def clearAllRestrictions(): SC[Unit] = SC(api => {
    api.call(passwd, "-u", User)

    val programsToUnlock = Constants.Restricted.Programs.flatMap(x => api.findExecutableInPath(x._1) ++ x._2)
    for (program <- programsToUnlock) {
      api.call(chmod, "755", Tag.unwrap(program))
      api.call(chown, "root:root", Tag.unwrap(program))
    }

    for (projectLocation <- Constants.Restricted.Projects) {
      api.call(chown, s"$User:$User", Tag.unwrap(projectLocation))
      api.call(chmod, "755", Tag.unwrap(projectLocation))
    }
    val projects = Constants.Restricted.Projects.map(Tag.unwrap).map(new File(_)).flatMap(f => f.listFiles() ?? Array.empty)
    for (project <- projects) {
      api.call(chmod, "755", project.getCanonicalPath)
      api.call(chown, s"$User:$User", project.getCanonicalPath)
    }
  })

  private def decode(bytes: Array[Byte]): String = {
    new String(bytes, Constants.GlobalEncoding)
  }

}
