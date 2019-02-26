package monika.server.script.library

import monika.{Constants, Primitives}
import monika.Primitives.Filename
import monika.Constants.Restricted
import monika.Constants.Restricted.{Browsers, Programs, ProjectContainer}
import monika.server.script.Script
import monika.server.subprocess.Commands._
import monika.server.UseScalaz
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

    // find all browser folders indicated by project containers
    val browsers: Vector[String @@ Primitives.CanonicalPath] = Restricted.ProjectContainers
      .filter(_.itemsAre(Browsers))
      .flatMap(p ⇒ api.listFiles(p.path)).map(_._2)

    for (browserFolder <- browsers) {
      // kill any process whose exe is within the folder
      val procsToKill = procs.filter(p ⇒ unwrap(p.exe).startsWith(unwrap(browserFolder)))
      for (proc ← procsToKill) {
        api.call(kill, proc.pid.toString)
      }
    }
  })

  def restrictProjectsExcept(
    except: Vector[String @@ Filename] = Vector.empty,
    exceptBrowsers: Boolean = false
  ): IOS[Unit] = IOS(api => {
    val procs = api.listAllProcs()

    // disable write for all project containers
    for (container <- Restricted.ProjectContainers) {
      api.call(chown, "root:root", unwrap(container.path))
      api.call(chmod, "755", unwrap(container.path))

      val projects = api.listFiles(container.path)
      val (toUnlock, toLock) = projects.partition(p ⇒
        (except.contains(p._1)) ||
        (exceptBrowsers && container.itemsAre(Browsers))
      )

      // allow access for projects which are not restricted
      // if it is a program project folder, only read access is allowed
      // otherwise read/write access is allowed
      for ((_, project) <- toUnlock) {
        if (container.itemsAre(Programs)) {
          api.call(chown, s"root:root", project |> unwrap)
          api.call(chmod, "755", project |> unwrap)
        } else {
          api.call(chown, s"$User:$User", project |> unwrap)
          api.call(chmod, "755", project |> unwrap)
        }
      }

      // disable read/write for projects which are restricted
      for ((_, project) <- toLock) {
        // kill any process whose exe is within the project
        // or is the project itself
        val procsToKill = procs.filter(p ⇒ unwrap(p.exe).startsWith(unwrap(project)))
        for (proc ← procsToKill) {
          api.call(kill, proc.pid.toString)
        }

        // perform chmod after the process has been killed
        api.call(chown, "root:root", project |> unwrap)
        api.call(chmod, "700", project |> unwrap)
      }
    }

  })

  /**
    * clears any restrictions previously applied on the user
    * i.e. sudo, programs, projects
    */
  def clearAllRestrictions(): IOS[Unit] = IOS(api => {
    api.call(passwd, "-u", User)

    // allow read/write access for all project containers
    for (ProjectContainer(path, _) <- Restricted.ProjectContainers) {
      api.call(chown, s"$User:$User", unwrap(path))
      api.call(chmod, "755", unwrap(path))
    }

    // allow access to all project folders
    // if it is a program project folder, only read access is allowed
    // otherwise read/write access is allowed
    for (container ← Restricted.ProjectContainers) {
      val projects = api.listFiles(container.path)
      for ((_, project) <- projects) {
        if (container.itemsAre(Programs)) {
          api.call(chown, s"root:root", project |> unwrap)
          api.call(chmod, "755", project |> unwrap)
        } else {
          api.call(chown, s"$User:$User", project |> unwrap)
          api.call(chmod, "755", project |> unwrap)
        }
      }
    }
  })

  private def decode(bytes: Array[Byte]): String = {
    new String(bytes, Constants.Encoding)
  }

}
