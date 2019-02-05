package monika.server.script.library

import monika.Primitives.Filename
import monika.server.Constants.Restricted
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

  def closeAllBrowsers(): IOS[Unit] = IOS(api ⇒ {
    val procs = api.listAllProcs()
    val browsers = Restricted.BrowserContainer |> api.listFiles
    for ((_, browserFolder) <- browsers) {
      // kill any process whose exe is within the folder
      val procsToKill = procs.filter(p ⇒ unwrap(p.exe).startsWith(unwrap(browserFolder)))
      for (proc ← procsToKill) {
        api.call(kill, proc.pid.toString)
      }
    }
  })

  def restrictProjectsExcept(except: Vector[String @@ Filename]): IOS[Unit] = IOS(api => {
    val projects = Restricted.ProjectContainers.flatMap(api.listFiles)
    val (toUnlock, toLock) = projects.partition(p ⇒ except.contains(p._1))

    // disable write for all project containers
    for (projectContainer <- Restricted.ProjectContainers) {
      api.call(chown, "root:root", unwrap(projectContainer))
      api.call(chmod, "755", unwrap(projectContainer))
    }

    // allow read/write for projects which are not restricted
    for ((_, project) <- toUnlock) {
      api.call(chown, s"$User:$User", project |> unwrap)
      api.call(chmod, "755", project |> unwrap)
    }

    // disable read/write for projects which are restricted
    val procs = api.listAllProcs()
    for ((_, project) <- toLock) {
      api.call(chown, "root:root", project |> unwrap)
      api.call(chmod, "700", project |> unwrap)

      // kill any process whose exe is within the project
      // or is the project itself
      val procsToKill = procs.filter(p ⇒ unwrap(p.exe).startsWith(unwrap(project)))
      for (proc ← procsToKill) {
        api.call(kill, proc.pid.toString)
      }
    }
  })

  /**
    * clears any restrictions previously applied on the user
    * i.e. sudo, programs, projects
    */
  def clearAllRestrictions(): IOS[Unit] = IOS(api => {
    api.call(passwd, "-u", User)

    // allow read/write for all project containers
    for (projectLocation <- Restricted.ProjectContainers) {
      api.call(chown, s"$User:$User", unwrap(projectLocation))
      api.call(chmod, "755", unwrap(projectLocation))
    }

    // allow read/write for all project folders
    val projects = Restricted.ProjectContainers.flatMap(api.listFiles)
    for ((_, project) <- projects) {
      api.call(chown, s"$User:$User", project |> unwrap)
      api.call(chmod, "755", project |> unwrap)
    }
  })

  private def decode(bytes: Array[Byte]): String = {
    new String(bytes, Constants.GlobalEncoding)
  }

}
