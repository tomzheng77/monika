package monika.server

import java.io.File

import monika.Primitives.FileName
import monika.server.Constants.UtilityPrograms._
import monika.server.Subprocess._
import scalaz.syntax.id._
import scalaz.{@@, Tag}

object Restrictions {

  // the environment of this user will be affected
  private val User = Constants.MonikaUser

  def restrictLogin(): Unit = {
    Subprocess.call(passwd, "-l", User)
  }

  /**
    * - disassociates the user from the "wheel" secondary group
    * - this takes effect upon next login
    */
  def removeFromWheelGroup(): Unit = {
    val primaryGroup = Subprocess.call(id, "-gn", User).stdout |> decode
    val oldGroups = Subprocess.call(groups, User).stdout |> decode |> (_.trim()) |> (_.split(' ').drop(2).map(_.trim).toSet)
    val newGroups = oldGroups.filter(g => g != primaryGroup && g!= "wheel")
    Subprocess.call(usermod, "-G", newGroups.mkString(","), User)
  }

  def restrictProgramsExcept(except: Vector[String @@ FileName]): Unit = {
    val programs = Constants.Restricted.Programs
      .filterNot(except.contains)
      .flatMap(Subprocess.findProgramLocation)

    for (program <- programs) {
      call(chmod, "700", Tag.unwrap(program))
      call(chown, "root:root", Tag.unwrap(program))
    }
  }

  def restrictProjectsExcept(except: Vector[String @@ FileName]): Unit = {
    val projectNameSet = except.map(Tag.unwrap).toSet
    val projects = Constants.Restricted.Projects.map(Tag.unwrap).map(new File(_)).flatMap(f => f.listFiles() ?? Array.empty)
    val (toUnlock, toLock) = projects.partition(f => projectNameSet contains f.getName)

    for (projectLocation <- Constants.Restricted.Projects) {
      call(chown, "root:root", Tag.unwrap(projectLocation))
      call(chmod, "777", Tag.unwrap(projectLocation))
    }
    for (project <- toUnlock) {
      call(chmod, "755", project.getCanonicalPath)
      call(chown, s"${Constants.MonikaUser}:${Constants.MonikaUser}", project.getCanonicalPath)
    }
    for (project <- toLock) {
      call(chmod, "700", project.getCanonicalPath)
      call(chown, "root:root", project.getCanonicalPath)
    }
  }

  /**
    * clears any restrictions previously applied on the user
    * i.e. sudo, programs, projects
    */
  def clearAllRestrictions(): Unit = {
    Subprocess.call(passwd, "-u", User)

    val primaryGroup = Subprocess.call(id, "-gn", User).stdout |> decode
    val oldGroups = Subprocess.call(groups, User).stdout |> decode |> (_.trim()) |> (_.split(' ').drop(2).map(_.trim).toSet)
    val newGroups = oldGroups.filter(g => g != primaryGroup) + "wheel"
    Subprocess.call(usermod, "-G", newGroups.mkString(","), User)

    val programs = Constants.Restricted.Programs.flatMap(Subprocess.findProgramLocation)
    for (program <- programs) {
      call(chmod, "755", Tag.unwrap(program))
      call(chown, "root:root", Tag.unwrap(program))
    }

    for (projectLocation <- Constants.Restricted.Projects) {
      call(chown, s"${Constants.MonikaUser}:${Constants.MonikaUser}", Tag.unwrap(projectLocation))
      call(chmod, "755", Tag.unwrap(projectLocation))
    }
    val projects = Constants.Restricted.Projects.map(Tag.unwrap).map(new File(_)).flatMap(f => f.listFiles() ?? Array.empty)
    for (project <- projects) {
      call(chmod, "755", project.getCanonicalPath)
      call(chown, s"${Constants.MonikaUser}:${Constants.MonikaUser}", project.getCanonicalPath)
    }
  }

  private def decode(bytes: Array[Byte]): String = {
    new String(bytes, Constants.GlobalEncoding)
  }

}
