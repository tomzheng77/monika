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

  /**
    * - disassociates the user from the "wheel" secondary group
    * - this takes effect upon next login
    */
  def removeFromWheelGroup(): SC[Unit] = SC(api => {
    val primaryGroup = api.call(id, "-gn", User).stdout |> decode
    val oldGroups = api.call(groups, User).stdout |> decode |> (_.trim()) |> (_.split(' ').drop(2).map(_.trim).toSet)
    val newGroups = oldGroups.filter(g => g != primaryGroup && g!= "wheel")
    api.call(usermod, "-G", newGroups.mkString(","), User)
  })

  def forceLogout(): SC[Unit] = SC(api => {
    api.call(killall, "-u", User, "i3")
    api.call(killall, "-u", User, "gnome-session-binary")
  })

  def restrictProgramsExcept(except: Vector[String @@ FileName]): SC[Unit] = SC(api => {
    val (toUnlock, toLock) = Constants.Restricted.Programs
      .partition(except.contains) |> (pair => (
        pair._1.flatMap(api.findExecutableInPath),
        pair._2.flatMap(api.findExecutableInPath)
      ))

    for (program <- toUnlock) {
      api.call(chmod, "755", Tag.unwrap(program))
      api.call(chown, "root:root", Tag.unwrap(program))
    }
    for (program <- toLock) {
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
      api.call(chown, s"${Constants.MonikaUser}:${Constants.MonikaUser}", project.getCanonicalPath)
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

    val primaryGroup = api.call(id, "-gn", User).stdout |> decode
    val oldGroups = api.call(groups, User).stdout |> decode |> (_.trim()) |> (_.split(' ').drop(2).map(_.trim).toSet)
    val newGroups = oldGroups.filter(g => g != primaryGroup) + "wheel"
    api.call(usermod, "-G", newGroups.mkString(","), User)

    val programs = Constants.Restricted.Programs.flatMap(api.findExecutableInPath)
    for (program <- programs) {
      api.call(chmod, "755", Tag.unwrap(program))
      api.call(chown, "root:root", Tag.unwrap(program))
    }

    for (projectLocation <- Constants.Restricted.Projects) {
      api.call(chown, s"${Constants.MonikaUser}:${Constants.MonikaUser}", Tag.unwrap(projectLocation))
      api.call(chmod, "755", Tag.unwrap(projectLocation))
    }
    val projects = Constants.Restricted.Projects.map(Tag.unwrap).map(new File(_)).flatMap(f => f.listFiles() ?? Array.empty)
    for (project <- projects) {
      api.call(chmod, "755", project.getCanonicalPath)
      api.call(chown, s"${Constants.MonikaUser}:${Constants.MonikaUser}", project.getCanonicalPath)
    }
  })

  private def decode(bytes: Array[Byte]): String = {
    new String(bytes, Constants.GlobalEncoding)
  }

}
