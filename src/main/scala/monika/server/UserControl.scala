package monika.server

import java.io.File

import monika.Primitives.FileName
import monika.server.Constants.CallablePrograms._
import monika.server.Constants.Locations
import monika.server.Subprocess._
import scalaz.{@@, Tag}
import scalaz.syntax.id._

object UserControl {

  // the environment of this user will be affected
  private val User = Constants.MonikaUser

  def disableLogin(): Unit = {
    Subprocess.call(passwd, "-l", User)
  }

  /**
    * removes the user from the "wheel" group
    * so they cannot perform sudo actions
    */
  def removeFromWheelGroup(): Unit = {
    val primaryGroup = Subprocess.call(id, "-gn", User).stdout |> decode
    val oldGroups = Subprocess.call(groups, User).stdout |> decode |> (_.trim()) |> (_.split(' ').drop(2).map(_.trim).toSet)
    val newGroups = oldGroups.filter(g => g != primaryGroup && g!= "wheel")
    Subprocess.call(usermod, "-G", newGroups.mkString(","), User)
  }

  def restrictPrograms(except: Vector[String @@ FileName]): Unit = {
    val programs = Constants.RestrictedPrograms
      .filterNot(except.contains)
      .flatMap(Subprocess.findProgramLocation)

    programs.map(l => Command(chmod, "700", Tag.unwrap(l))).foreach(callCommand)
    programs.map(l => Command(chown, "root:root", Tag.unwrap(l))).foreach(callCommand)
  }

  def restrictProjects(except: Vector[String @@ FileName]): Unit = {
    val projectNameSet = except.map(Tag.unwrap).toSet
    val projects = Option(new File(Locations.ProjectRoot).listFiles()).getOrElse(Array.empty)
    val (toUnlock, toLock) = projects.partition(f => projectNameSet contains f.getName)

    toUnlock.map(f => Command(chmod, "755", f.getCanonicalPath)).foreach(callCommand)
    toUnlock.map(f => Command(chown, s"${Constants.MonikaUser}:${Constants.MonikaUser}", f.getCanonicalPath)).foreach(callCommand)
    toLock.map(f => Command(chmod, "700", f.getCanonicalPath)).foreach(callCommand)
    toLock.map(f => Command(chown, "root:root", f.getCanonicalPath)).foreach(callCommand)
  }

  /**
    * clears any restrictions previously applied on the user
    * i.e. sudo, programs, projects
    */
  def unlock(): Unit = {
    Subprocess.call(passwd, "-u", User)

    val primaryGroup = Subprocess.call(id, "-gn", User).stdout |> decode
    val oldGroups = Subprocess.call(groups, User).stdout |> decode |> (_.trim()) |> (_.split(' ').drop(2).map(_.trim).toSet)
    val newGroups = oldGroups.filter(g => g != primaryGroup) + "wheel"
    Subprocess.call(usermod, "-G", newGroups.mkString(","), User)

    val programs = Constants.RestrictedPrograms.flatMap(Subprocess.findProgramLocation)
    programs.map(l => Command(chmod, "755", Tag.unwrap(l))).foreach(callCommand)
    programs.map(l => Command(chown, "root:root", Tag.unwrap(l))).foreach(callCommand)

    val projects = Option(new File(Locations.ProjectRoot).listFiles()).getOrElse(Array.empty)
    projects.map(f => Command(chmod, "755", f.getCanonicalPath)).foreach(callCommand)
    projects.map(f => Command(chown, s"${Constants.MonikaUser}:${Constants.MonikaUser}", f.getCanonicalPath)).foreach(callCommand)
  }

  private def decode(bytes: Array[Byte]): String = {
    new String(bytes, Constants.GlobalEncoding)
  }

}
