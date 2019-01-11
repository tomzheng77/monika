package monika.server

import monika.Primitives.FileName
import monika.server.Constants.CallablePrograms._
import scalaz.@@
import scalaz.syntax.id._

object UserControl {

  // the environment of this user will be affected
  private val User = Constants.MonikaUser

  /**
    * removes the user from the "wheel" group
    * so they cannot perform sudo actions
    */
  def removeFromWheelGroup(): Unit = {
    val primaryGroup = Subprocess.call(id, "-gn", User).stdout |> decode
    val oldGroups = Subprocess.call(groups, User).stdout |> decode |> (_.trim()) |> (_.split(' ').drop(3).map(_.trim).toSet)
    val newGroups = oldGroups.filter(g => g != primaryGroup && g!= "wheel")
    Subprocess.call(usermod, "-G", newGroups.mkString(","), User)
  }

  def restrictPrograms(programs: Vector[String @@ FileName]): Unit = {

  }

  def restrictProjects(projects: Vector[String @@ FileName]): Unit = {
  }

  /**
    * clears any restrictions previously applied on the user
    * i.e. sudo, programs, projects
    */
  def unlock(): Unit = {

  }

  private def decode(bytes: Array[Byte]): String = {
    new String(bytes, Constants.GlobalEncoding)
  }

}
