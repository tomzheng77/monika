package monika.server.script.library

import java.io.File

import monika.Primitives.FileName
import monika.server.script.Script
import monika.server.subprocess.Commands._
import monika.server.{Constants, UseScalaz}
import scalaz.@@
import scalaz.Tag.unwrap

trait RestrictionOps extends UseScalaz with ReaderOps { self: Script =>

  // the environment of this user will be affected
  private val User = Constants.MonikaUser

  def restrictLogin(): IOS[Unit] = IOS(api => {
    api.call(passwd, "-l", User)
  })

  def forceLogout(): IOS[Unit] = IOS(api => {
    api.call(killall, "-u", User, "i3")
    api.call(killall, "-u", User, "gnome-session-binary")
  })

  def removeFromWheelGroup(): IOS[Unit] = IOS(api => {
    val primaryGroup = api.call(id, "-gn", User).stdout |> decode
    val oldGroups = api.call(groups, User).stdout |> decode |> (_.trim()) |> (_.split(' ').drop(2).map(_.trim).toSet)
    val newGroups = oldGroups.filter(g => g != primaryGroup && g!= "wheel")
    api.call(usermod, "-G", newGroups.mkString(","), User)
  })

  def addToWheelGroup(): IOS[Unit] = IOS(api => {
    val primaryGroup = api.call(id, "-gn", User).stdout |> decode
    val oldGroups = api.call(groups, User).stdout |> decode |> (_.trim()) |> (_.split(' ').drop(2).map(_.trim).toSet)
    val newGroups = oldGroups.filter(g => g != primaryGroup) + "wheel"
    api.call(usermod, "-G", newGroups.mkString(","), User)
  })

  def restrictProgramsExcept(except: Vector[String @@ FileName]): IOS[Unit] = IOS(api => {
    val (toUnlock, toLock) = Constants.Restricted.Programs
      .partition(pair => except.contains(pair._1)) |>
      (pair => (
        pair._1.flatMap(x => api.findExecutableInPath(x._1) ++ x._2),
        pair._2.flatMap(x => api.findExecutableInPath(x._1) ++ x._2)
      ))

    for (program <- toUnlock) {
      api.call(chmod, "755", unwrap(program))
      api.call(chown, "root:root", unwrap(program))
    }
    for (program <- toLock) {
      api.call(killall, "-u", User, unwrap(program))
      api.call(chmod, "700", unwrap(program))
      api.call(chown, "root:root", unwrap(program))
    }
  })

  def restrictProjectsExcept(except: Vector[String @@ FileName]): IOS[Unit] = IOS(api => {
    val projectNameSet = except.map(unwrap).toSet
    val projects = Constants.Restricted.Projects.map(unwrap).map(new File(_)).flatMap(f => f.listFiles() ?? Array.empty)
    val (toUnlock, toLock) = projects.partition(f => projectNameSet contains f.getName)

    for (projectLocation <- Constants.Restricted.Projects) {
      api.call(chown, "root:root", unwrap(projectLocation))
      api.call(chmod, "777", unwrap(projectLocation))
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
  def clearAllRestrictions(): IOS[Unit] = IOS(api => {
    api.call(passwd, "-u", User)

    val programsToUnlock = Constants.Restricted.Programs.flatMap(x => api.findExecutableInPath(x._1) ++ x._2)
    for (program <- programsToUnlock) {
      api.call(chmod, "755", unwrap(program))
      api.call(chown, "root:root", unwrap(program))
    }

    for (projectLocation <- Constants.Restricted.Projects) {
      api.call(chown, s"$User:$User", unwrap(projectLocation))
      api.call(chmod, "755", unwrap(projectLocation))
    }
    val projects = Constants.Restricted.Projects.flatMap(api.listFiles)
    for (project <- projects) {
      api.call(chmod, "755", project |> unwrap)
      api.call(chown, s"$User:$User", project |> unwrap)
    }
  })

  private def decode(bytes: Array[Byte]): String = {
    new String(bytes, Constants.GlobalEncoding)
  }

}
