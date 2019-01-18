package monika.server.script

import java.io.PrintWriter
import java.time.LocalDateTime

import monika.server.Structs.FutureAction
import monika.server.proxy.{HTMLPrefixFilter, ProxyServer}

import scala.util.Try

/**
  * - locks onto the specified website for a fixed amount of time
  * - HTTP pages will be intercepted unless starting with the provided prefix
  * - will call "unlock" after time has expired
  */
object LockSite extends Script with RequireRoot {

  override def run(args: Vector[String], out: PrintWriter): Unit = {
    if (args.size != 2) {
      out.println("usage: lock-site <site> <minutes>")
    } else if (Try(args(1).toInt).filter(_ > 0).isFailure) {
      out.println(s"minutes must be a positive integer")
    } else {
      val site = args(0)
      val minutes = args(1).toInt
      ProxyServer.startOrRestart(HTMLPrefixFilter(Set(site)))
      val nowTime = LocalDateTime.now()
      FutureAction(nowTime.plusMinutes(minutes), Unlock)
      out.println(s"locked onto site for $minutes minutes")
    }
  }

}
