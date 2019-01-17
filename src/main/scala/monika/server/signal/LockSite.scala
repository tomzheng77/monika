package monika.server.signal

import java.io.PrintWriter
import java.time.LocalDateTime

import monika.server.LittleProxy.ProxySettings
import monika.server.Structs.FutureAction
import monika.server.{Configuration, LittleProxy}

import scala.util.Try

/**
  * - locks onto the specified website for a fixed amount of time
  * - HTTP pages will be intercepted unless starting with the provided prefix
  * - will call "unlock" after time has expired
  */
object LockSite extends Script {

  override def run(args: Vector[String], out: PrintWriter): Unit = {
    val profiles = Configuration.readProfileDefinitions()
    lazy val name = args.head
    if (args.size != 2) {
      out.println("usage: lock-site <site> <minutes>")
    } else if (Try(args(1).toInt).filter(_ > 0).isFailure) {
      out.println(s"minutes must be a positive integer")
    } else {
      val site = args(0)
      val minutes = args(1).toInt
      LittleProxy.startOrRestart(ProxySettings(
        transparent = false,
        allowHtmlPrefix = Vector(site)
      ))
      val nowTime = LocalDateTime.now()
      FutureAction(nowTime.plusMinutes(minutes), Unlock)
      out.println("set-profile success")
    }
  }

}
